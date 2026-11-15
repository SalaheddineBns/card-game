import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { GamesService, InvitationDto, InvitationsService, PlayersService } from '../../core/api';
import { AuthStore } from '../../core/auth/auth-store';
import { NotificationService } from '../../core/notification/notification.service';
import {
  GameSettings,
  InvitationEvent,
  PlayerStatus,
  PresenceEvent,
} from '../../core/realtime/protocol';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { InvitationView, toInvitationView } from '../../shared/game/game-mappers';

export interface OnlinePlayer {
  username: string;
  status: PlayerStatus;
}

function sameName(a: string, b: string): boolean {
  return a.toLowerCase() === b.toLowerCase();
}

function byName(a: OnlinePlayer, b: OnlinePlayer): number {
  return a.username.localeCompare(b.username, 'fr', { sensitivity: 'base' });
}

/**
 * Lobby state (rows 7-10): online players, pending invitations (sent and received) and the
 * player's live game. Provided by `LobbyPage`, so its STOMP subscriptions end with the page.
 */
@Injectable()
export class LobbyStore {
  private readonly players = inject(PlayersService);
  private readonly invitationsApi = inject(InvitationsService);
  private readonly games = inject(GamesService);
  private readonly realtime = inject(RealtimeService);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _onlinePlayers = signal<OnlinePlayer[]>([]);
  private readonly _invitations = signal<InvitationView[]>([]);
  private readonly _currentGameId = signal<number | null>(null);
  private readonly _sending = signal(false);
  private readonly _responding = signal(false);

  private readonly me = computed(() => this.auth.currentUser()?.username ?? '');

  /** Other online players, by name (the current player is excluded, as in legacy). */
  readonly onlinePlayers = this._onlinePlayers.asReadonly();
  readonly currentGameId = this._currentGameId.asReadonly();
  readonly sending = this._sending.asReadonly();
  readonly responding = this._responding.asReadonly();

  /** The pending invitation this player sent, if any. */
  readonly outgoing = computed(
    () => this._invitations().find((i) => sameName(i.inviter, this.me())) ?? null,
  );
  /** The oldest pending invitation this player received, if any. */
  readonly incoming = computed(
    () => this._invitations().find((i) => !sameName(i.inviter, this.me())) ?? null,
  );

  /** Subscribes to presence, invitations and game starts, then loads the initial state. */
  init(): void {
    this.realtime
      .presence()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.onPresence(event));
    this.realtime
      .invitations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.onInvitation(event));
    this.realtime
      .gameLifecycle()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => void this.router.navigate(['/games', event.gameId]));

    void this.loadOnlinePlayers();
    void this.loadPendingInvitations();
    void this.loadCurrentGame();
  }

  /** `POST /api/invitations`. Errors (409 offline / in game...) are toasted by the interceptor. */
  async invite(invitees: string[], settings: GameSettings): Promise<boolean> {
    this._sending.set(true);
    try {
      const dto = await firstValueFrom(
        this.invitationsApi.createInvitation({ createInvitationRequest: { invitees, settings } }),
      );
      this.upsert(toInvitationView(dto));
      this.notifications.info('Invitation envoyée', `En attente de ${invitees.join(', ')}.`);
      return true;
    } catch {
      return false;
    } finally {
      this._sending.set(false);
    }
  }

  async accept(id: number): Promise<void> {
    await this.respond(id, () => this.invitationsApi.acceptInvitation({ id }));
  }

  async decline(id: number): Promise<void> {
    await this.respond(id, () => this.invitationsApi.declineInvitation({ id }));
  }

  hasAccepted(invitation: InvitationView): boolean {
    return invitation.accepted.some((name) => sameName(name, this.me()));
  }

  private async respond(id: number, call: () => Observable<InvitationDto>): Promise<void> {
    this._responding.set(true);
    try {
      const invitation = toInvitationView(await firstValueFrom(call()));
      if (invitation.status === 'PENDING') {
        this.upsert(invitation);
      } else {
        this.remove(invitation.id);
      }
    } catch {
      // 409: no longer pending (expired, cancelled). The interceptor already said why.
      this.remove(id);
    } finally {
      this._responding.set(false);
    }
  }

  private async loadOnlinePlayers(): Promise<void> {
    try {
      const dtos = await firstValueFrom(this.players.getOnlinePlayers());
      const loaded = dtos
        .filter((p) => p.username && !sameName(p.username, this.me()))
        .map((p): OnlinePlayer => ({ username: p.username ?? '', status: p.status ?? 'ONLINE' }));
      // Keep anything presence events added while the request was in flight.
      this._onlinePlayers.update((current) => mergePlayers(loaded, current));
    } catch {
      // Toasted by the interceptor; presence events still fill the list.
    }
  }

  private async loadPendingInvitations(): Promise<void> {
    try {
      const dtos = await firstValueFrom(this.invitationsApi.getPendingInvitations());
      for (const dto of dtos) {
        const invitation = toInvitationView(dto);
        if (invitation.status === 'PENDING') {
          this.upsert(invitation);
        }
      }
    } catch {
      // Toasted by the interceptor.
    }
  }

  private async loadCurrentGame(): Promise<void> {
    try {
      const snapshot = await firstValueFrom(this.games.getCurrentGame());
      this._currentGameId.set(snapshot?.gameId ?? null);
    } catch {
      this._currentGameId.set(null);
    }
  }

  private onPresence(event: PresenceEvent): void {
    if (sameName(event.username, this.me())) {
      return;
    }
    switch (event.type) {
      case 'PlayerOnline':
      case 'PlayerStatusChanged':
        this._onlinePlayers.update((players) =>
          mergePlayers(players, [{ username: event.username, status: event.status }]),
        );
        break;
      case 'PlayerOffline':
        this._onlinePlayers.update((players) =>
          players.filter((p) => !sameName(p.username, event.username)),
        );
        break;
    }
  }

  private onInvitation(event: InvitationEvent): void {
    if (event.type === 'InvitationReceived') {
      this.upsert({
        id: event.invitationId,
        inviter: event.inviter,
        invitees: event.invitees,
        accepted: [],
        settings: event.settings,
        status: 'PENDING',
        declinedBy: null,
        expiresAt: event.expiresAt,
      });
      return;
    }
    const known = this._invitations().find((i) => i.id === event.invitationId);
    if (!known) {
      return;
    }
    switch (event.status) {
      case 'PENDING': {
        const newlyAccepted = event.accepted.filter((n) => !known.accepted.includes(n));
        this.upsert({ ...known, accepted: event.accepted });
        if (sameName(known.inviter, this.me()) && newlyAccepted.length > 0) {
          this.notifications.info('Invitation acceptée', `${newlyAccepted.join(', ')} a accepté.`);
        }
        break;
      }
      case 'ACCEPTED':
        this.remove(known.id);
        this.notifications.success('Tout le monde a accepté', 'La partie commence !');
        break;
      case 'DECLINED':
        this.remove(known.id);
        this.notifications.warn(
          'Invitation annulée',
          event.declinedBy && !sameName(event.declinedBy, this.me())
            ? `${event.declinedBy} a refusé l'invitation.`
            : "Vous avez refusé l'invitation.",
        );
        break;
      case 'EXPIRED':
        this.remove(known.id);
        this.notifications.warn(
          'Invitation expirée',
          'Tous les joueurs n’ont pas répondu à temps.',
        );
        break;
    }
  }

  private upsert(invitation: InvitationView): void {
    this._invitations.update((list) =>
      list.some((i) => i.id === invitation.id)
        ? list.map((i) => (i.id === invitation.id ? invitation : i))
        : [...list, invitation],
    );
  }

  private remove(id: number): void {
    this._invitations.update((list) => list.filter((i) => i.id !== id));
  }
}

/** `updates` win over `base`; result sorted by name. */
function mergePlayers(base: OnlinePlayer[], updates: OnlinePlayer[]): OnlinePlayer[] {
  const merged = new Map(base.map((p) => [p.username.toLowerCase(), p]));
  for (const p of updates) {
    merged.set(p.username.toLowerCase(), p);
  }
  return [...merged.values()].sort(byName);
}
