import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { filter, firstValueFrom, pairwise } from 'rxjs';
import { GamesService } from '../../core/api';
import { AuthStore } from '../../core/auth/auth-store';
import { problemOf } from '../../core/http/problem-detail';
import { SKIP_ERROR_TOAST } from '../../core/http/skip-error-toast';
import { NotificationService } from '../../core/notification/notification.service';
import { Claim, GameEvent, UserErrorEvent } from '../../core/realtime/protocol';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { GameView, toGameView } from '../../shared/game/game-mappers';

/** The store handles every snapshot error itself (409 → result, 410 → lobby...). */
const QUIET = { context: new HttpContext().set(SKIP_ERROR_TOAST, true) };

/** If the server never answers a claim (lost frame), the buttons unlock after this. */
const CLAIM_TIMEOUT_MS = 3_000;

export type ClaimFeedback =
  | { kind: 'scored'; message: string }
  | { kind: 'scored-by-other'; message: string }
  | { kind: 'wrong'; message: string }
  | { kind: 'stale'; message: string };

export type LoadState = 'loading' | 'ready' | 'failed';

/**
 * One live game (rows 10-12). Provided by `GamePage`, so its subscriptions end with the page.
 * Client rules (docs/realtime-protocol.md, G7): subscribe to the game topic first, then load the
 * snapshot, and drop buffered events whose `dealVersion` is not newer than the snapshot's.
 */
@Injectable()
export class GameStore {
  private readonly api = inject(GamesService);
  private readonly realtime = inject(RealtimeService);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  private gameId = 0;
  private buffer: GameEvent[] | null = null;
  private claimTimer: ReturnType<typeof setTimeout> | undefined;

  private readonly _game = signal<GameView | null>(null);
  private readonly _loadState = signal<LoadState>('loading');
  private readonly _pendingClaim = signal<number | null>(null);
  private readonly _feedback = signal<ClaimFeedback | null>(null);

  readonly game = this._game.asReadonly();
  readonly loadState = this._loadState.asReadonly();
  readonly feedback = this._feedback.asReadonly();
  /** A claim is on its way for the current deal: one claim per deal. */
  readonly claimPending = computed(() => this._pendingClaim() !== null);
  readonly me = computed(() => this.auth.currentUser()?.username ?? '');

  constructor() {
    // After a reconnect rx-stomp re-subscribes, but events sent meanwhile are lost: resync (D17).
    toObservable(this.realtime.connectionState)
      .pipe(
        pairwise(),
        filter(([before, now]) => before !== 'connected' && now === 'connected'),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        if (this._game()) {
          void this.loadSnapshot();
        }
      });
    this.destroyRef.onDestroy(() => clearTimeout(this.claimTimer));
  }

  open(gameId: number): void {
    this.gameId = gameId;
    this.buffer = [];
    this.realtime
      .watchGame(gameId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => (this.buffer ? this.buffer.push(event) : this.apply(event)));
    this.realtime
      .errors()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.onError(event));
    void this.loadSnapshot();
  }

  retry(): void {
    this._loadState.set('loading');
    void this.loadSnapshot();
  }

  claim(claim: Claim): void {
    const game = this._game();
    if (!game || this._pendingClaim() !== null) {
      return;
    }
    const dealVersion = game.deal.dealVersion;
    this._pendingClaim.set(dealVersion);
    this._feedback.set(null);
    this.realtime.sendClaim(this.gameId, { claim, dealVersion });
    clearTimeout(this.claimTimer);
    this.claimTimer = setTimeout(() => this.unlockClaims(), CLAIM_TIMEOUT_MS);
  }

  private async loadSnapshot(): Promise<void> {
    try {
      const snapshot = toGameView(
        await firstValueFrom(this.api.getGame({ id: this.gameId }, 'body', false, QUIET)),
      );
      if (snapshot.finished) {
        await this.goToResult();
        return;
      }
      this._game.set(snapshot);
      this._loadState.set('ready');
      const buffered = this.buffer ?? [];
      this.buffer = null;
      buffered.forEach((event) => this.apply(event));
    } catch (error: unknown) {
      await this.onSnapshotError(error);
    }
  }

  private async onSnapshotError(error: unknown): Promise<void> {
    const status = error instanceof HttpErrorResponse ? error.status : null;
    const problem = problemOf(error);
    switch (status) {
      case 409: // finished
        await this.goToResult();
        return;
      case 410: // abandoned (D25)
      case 403: // not a player
      case 404:
        this.notifications.warn(
          problem?.title ?? 'Partie indisponible',
          problem?.detail ?? "Cette partie n'est plus disponible.",
        );
        await this.router.navigate(['/lobby']);
        return;
      case 401:
        return; // the interceptor sends the player to the login page
      default:
        this.notifications.error(
          'Partie introuvable',
          status === 0 ? 'Serveur injoignable.' : problem?.detail,
        );
        this._loadState.set('failed');
    }
  }

  private apply(event: GameEvent): void {
    if (event.type === 'GameFinished') {
      void this.goToResult();
      return;
    }
    const game = this._game();
    if (!game || event.deal.dealVersion <= game.deal.dealVersion) {
      return; // older than what we show (G7)
    }
    this.unlockClaims();
    if (event.type === 'ScoresUpdated') {
      this._game.set({ ...game, scores: event.scores, deal: event.deal });
      this._feedback.set(
        event.scoredBy.toLowerCase() === this.me().toLowerCase()
          ? { kind: 'scored', message: 'Bien joué ! +1 point.' }
          : { kind: 'scored-by-other', message: `${event.scoredBy} marque un point !` },
      );
    } else {
      this._game.set({
        ...game,
        round: event.round,
        roundEndsAt: event.roundEndsAt,
        scores: event.scores,
        deal: event.deal,
      });
      this._feedback.set(null);
      this.notifications.info(
        'Temps écoulé !',
        `Manche suivante : ${event.round} / ${event.totalRounds}.`,
      );
    }
  }

  private onError(event: UserErrorEvent): void {
    if (event.type === 'Error') {
      this.notifications.error(event.title, event.detail);
      this.unlockClaims();
      return;
    }
    if (event.gameId !== this.gameId) {
      return;
    }
    this.unlockClaims();
    this._feedback.set(
      event.reason === 'WRONG'
        ? { kind: 'wrong', message: 'Raté ! Ce n’était pas la bonne réponse.' }
        : { kind: 'stale', message: 'Trop tard ! Les cartes ont déjà changé.' },
    );
  }

  private unlockClaims(): void {
    clearTimeout(this.claimTimer);
    this._pendingClaim.set(null);
  }

  private async goToResult(): Promise<void> {
    await this.router.navigate(['/games', this.gameId, 'result'], { replaceUrl: true });
  }
}
