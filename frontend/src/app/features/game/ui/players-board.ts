import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Card } from '../../../core/realtime/protocol';
import { PlayingCard } from '../../../shared/ui/playing-card/playing-card';

export interface PlayerRow {
  player: string;
  score: number;
  card: Card | null;
  isMe: boolean;
}

/** Every player's card and score, in player order (legacy rows "name : score : [card]"). */
@Component({
  selector: 'app-players-board',
  imports: [PlayingCard],
  template: `
    <ul class="grid gap-3 sm:grid-cols-2" aria-label="Joueurs et scores">
      @for (row of rows(); track row.player) {
        <li
          class="flex items-center gap-4 rounded-xl border bg-surface-0 p-3 shadow-sm dark:bg-surface-900"
          [class.border-primary]="row.isMe"
          [class.border-surface]="!row.isMe"
          [attr.aria-current]="row.isMe ? 'true' : null"
        >
          @if (row.card; as card) {
            <app-playing-card [symbol]="card.symbol" [color]="card.color" />
          }
          <div class="min-w-0 flex-1">
            <p class="truncate font-semibold">
              {{ row.player }}
              @if (row.isMe) {
                <span class="text-sm font-normal text-muted-color">(vous)</span>
              }
            </p>
            <p class="text-sm text-muted-color">
              Score :
              <span class="text-2xl font-bold text-color tabular-nums">{{ row.score }}</span>
            </p>
          </div>
        </li>
      }
    </ul>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayersBoard {
  readonly rows = input.required<PlayerRow[]>();
}
