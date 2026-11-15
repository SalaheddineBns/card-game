import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  numberAttribute,
  untracked,
} from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { Claim } from '../../core/realtime/protocol';
import { PlayingCard } from '../../shared/ui/playing-card/playing-card';
import { injectServerNow } from '../../shared/time/now';
import { GameStore } from './game-store';
import { CLAIM_OPTIONS, ClaimButtons } from './ui/claim-buttons';
import { PlayerRow, PlayersBoard } from './ui/players-board';
import { RoundTimer } from './ui/round-timer';

const SHORTCUTS = new Map<string, Claim>([
  ...CLAIM_OPTIONS.map((o): [string, Claim] => [o.key.toLowerCase(), o.claim]),
  ['1', 'SAME_COLOR'],
  ['2', 'SAME_SYMBOL'],
  ['3', 'BOTH'],
]);

/** Live game (legacy game.jsp, redesigned). The server decides scores, rounds and the end. */
@Component({
  selector: 'app-game-page',
  imports: [
    ButtonModule,
    MessageModule,
    SkeletonModule,
    PlayingCard,
    ClaimButtons,
    PlayersBoard,
    RoundTimer,
  ],
  providers: [GameStore],
  templateUrl: './game-page.html',
  host: { '(document:keydown)': 'onKeydown($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GamePage {
  private readonly store = inject(GameStore);
  private readonly now = injectServerNow(100);

  /** Route param `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly game = this.store.game;
  protected readonly loadState = this.store.loadState;
  protected readonly feedback = this.store.feedback;
  protected readonly claimPending = this.store.claimPending;

  protected readonly remainingMs = computed(() => {
    const game = this.game();
    return game ? Math.max(0, Date.parse(game.roundEndsAt) - this.now()) : 0;
  });

  protected readonly rows = computed<PlayerRow[]>(() => {
    const game = this.game();
    if (!game) {
      return [];
    }
    const me = this.store.me().toLowerCase();
    return game.players.map((player) => ({
      player,
      score: game.scores.find((s) => s.player === player)?.score ?? 0,
      card: game.deal.playerCards.find((c) => c.player === player) ?? null,
      isMe: player.toLowerCase() === me,
    }));
  });

  protected readonly feedbackSeverity = computed(() => {
    switch (this.feedback()?.kind) {
      case 'scored':
        return 'success';
      case 'wrong':
        return 'error';
      case 'stale':
        return 'warn';
      default:
        return 'info';
    }
  });

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.store.open(id));
    });
  }

  protected claim(claim: Claim): void {
    this.store.claim(claim);
  }

  protected retry(): void {
    this.store.retry();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.repeat || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    const target = event.target;
    if (
      target instanceof HTMLElement &&
      (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
    ) {
      return;
    }
    const claim = SHORTCUTS.get(event.key.toLowerCase());
    if (claim) {
      event.preventDefault();
      this.store.claim(claim);
    }
  }
}
