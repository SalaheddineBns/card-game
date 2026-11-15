import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ProgressBarModule } from 'primeng/progressbar';
import { InvitationView } from '../../../shared/game/game-mappers';

/** Invitations expire 60 s after creation (D7). */
const INVITATION_SECONDS = 60;

/** A received invitation: who, which settings, time left, accept / decline. */
@Component({
  selector: 'app-invitation-dialog',
  imports: [ButtonModule, DialogModule, ProgressBarModule],
  templateUrl: './invitation-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvitationDialog {
  readonly invitation = input.required<InvitationView>();
  readonly secondsLeft = input.required<number>();
  /** The player already accepted and waits for the others. */
  readonly accepted = input(false);
  readonly busy = input(false);
  readonly accept = output<void>();
  readonly decline = output<void>();

  protected readonly header = computed(() => `Invitation de ${this.invitation().inviter}`);
  protected readonly otherInvitees = computed(() => this.invitation().invitees.join(', '));
  protected readonly progress = computed(() =>
    Math.round((this.secondsLeft() / INVITATION_SECONDS) * 100),
  );
  protected readonly countdownLabel = computed(() => `${this.secondsLeft()} s restantes`);
}
