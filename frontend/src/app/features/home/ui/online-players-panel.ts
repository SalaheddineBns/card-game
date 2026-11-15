import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { OnlinePlayer } from '../lobby-store';

interface PlayerRow extends OnlinePlayer {
  inputId: string;
  inGame: boolean;
  checked: boolean;
}

/** Online players with their status; available players can be ticked to invite them. */
@Component({
  selector: 'app-online-players-panel',
  imports: [FormsModule, CheckboxModule, TagModule],
  templateUrl: './online-players-panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnlinePlayersPanel {
  readonly players = input.required<OnlinePlayer[]>();
  /** Selected usernames. */
  readonly selected = model<string[]>([]);
  readonly disabled = input(false);

  protected readonly rows = computed<PlayerRow[]>(() =>
    this.players().map((p) => ({
      ...p,
      inputId: `invite-${p.username}`,
      inGame: p.status === 'IN_GAME',
      checked: this.selected().includes(p.username),
    })),
  );

  protected toggle(username: string, checked: boolean): void {
    this.selected.update((names) =>
      checked ? [...names, username] : names.filter((n) => n !== username),
    );
  }
}
