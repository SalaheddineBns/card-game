import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { GameSettings } from '../../core/realtime/protocol';
import { injectServerNow, secondsUntil } from '../../shared/time/now';
import { LobbyStore } from './lobby-store';
import { GameSetupForm } from './ui/game-setup-form';
import { InvitationDialog } from './ui/invitation-dialog';
import { OnlinePlayersPanel } from './ui/online-players-panel';

/**
 * Lobby (legacy home.jsp): online players, game setup and invitations.
 * `GameStarted` sends every player to `/games/:id` (handled by the store).
 */
@Component({
  selector: 'app-lobby-page',
  imports: [
    RouterLink,
    ButtonModule,
    MessageModule,
    GameSetupForm,
    InvitationDialog,
    OnlinePlayersPanel,
  ],
  providers: [LobbyStore],
  templateUrl: './lobby-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyPage {
  private readonly store = inject(LobbyStore);
  private readonly now = injectServerNow();

  protected readonly onlinePlayers = this.store.onlinePlayers;
  protected readonly currentGameId = this.store.currentGameId;
  protected readonly sending = this.store.sending;
  protected readonly responding = this.store.responding;
  protected readonly outgoing = this.store.outgoing;
  protected readonly incoming = this.store.incoming;

  private readonly _selected = signal<string[]>([]);
  /** Ticked players that are still online and available. */
  protected readonly selected = computed(() => {
    const available = new Set(
      this.onlinePlayers()
        .filter((p) => p.status === 'ONLINE')
        .map((p) => p.username),
    );
    return this._selected().filter((name) => available.has(name));
  });

  protected readonly incomingSecondsLeft = computed(() => {
    const invitation = this.incoming();
    return invitation ? secondsUntil(invitation.expiresAt, this.now()) : 0;
  });
  protected readonly incomingAccepted = computed(() => {
    const invitation = this.incoming();
    return invitation ? this.store.hasAccepted(invitation) : false;
  });

  protected readonly outgoingStatus = computed(() => {
    const invitation = this.outgoing();
    if (!invitation) {
      return null;
    }
    const waiting = invitation.invitees.filter((n) => !invitation.accepted.includes(n));
    return {
      accepted: invitation.accepted.join(', ') || 'personne pour l’instant',
      waiting: waiting.join(', '),
      secondsLeft: secondsUntil(invitation.expiresAt, this.now()),
    };
  });

  constructor() {
    this.store.init();
  }

  protected setSelected(names: string[]): void {
    this._selected.set(names);
  }

  protected async invite(settings: GameSettings): Promise<void> {
    if (await this.store.invite(this.selected(), settings)) {
      this._selected.set([]);
    }
  }

  protected accept(id: number): void {
    void this.store.accept(id);
  }

  protected decline(id: number): void {
    void this.store.decline(id);
  }
}
