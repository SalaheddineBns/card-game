import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { GameSettings } from '../../../core/realtime/protocol';
import { errorMessage, formEvents } from '../../../shared/forms/form-errors';

/** D10 limits and defaults. */
export const ROUNDS = { min: 1, max: 20, default: 3 } as const;
export const ROUND_SECONDS = { min: 5, max: 120, default: 10 } as const;

/** Game settings (rounds, seconds per round, D2) and the invite button. */
@Component({
  selector: 'app-game-setup-form',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './game-setup-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameSetupForm {
  private readonly fb = inject(NonNullableFormBuilder);

  /** Usernames ticked in the players panel. */
  readonly invitees = input.required<string[]>();
  readonly pending = input(false);
  /** E.g. while an invitation is already waiting for answers. */
  readonly locked = input(false);
  readonly invite = output<GameSettings>();

  protected readonly limits = { rounds: ROUNDS, seconds: ROUND_SECONDS };

  protected readonly form = this.fb.group({
    totalRounds: this.fb.control<number | null>(ROUNDS.default, [
      Validators.required,
      Validators.min(ROUNDS.min),
      Validators.max(ROUNDS.max),
    ]),
    roundSeconds: this.fb.control<number | null>(ROUND_SECONDS.default, [
      Validators.required,
      Validators.min(ROUND_SECONDS.min),
      Validators.max(ROUND_SECONDS.max),
    ]),
  });

  private readonly formEvents = formEvents(this.form);
  protected readonly errors = computed(() => {
    this.formEvents();
    return {
      totalRounds: errorMessage(this.form.controls.totalRounds, {
        required: 'Le nombre de manches est obligatoire.',
        min: `Au moins ${ROUNDS.min} manche.`,
        max: `Au plus ${ROUNDS.max} manches.`,
      }),
      roundSeconds: errorMessage(this.form.controls.roundSeconds, {
        required: 'La durée d’une manche est obligatoire.',
        min: `Au moins ${ROUND_SECONDS.min} secondes.`,
        max: `Au plus ${ROUND_SECONDS.max} secondes.`,
      }),
    };
  });

  protected readonly inviteesLabel = computed(() => {
    const names = this.invitees();
    return names.length === 0 ? 'Aucun joueur sélectionné' : names.join(', ');
  });
  protected readonly canSubmit = computed(
    () => this.invitees().length > 0 && !this.pending() && !this.locked(),
  );

  protected reset(): void {
    this.form.reset();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { totalRounds, roundSeconds } = this.form.getRawValue();
    if (totalRounds === null || roundSeconds === null || !this.canSubmit()) {
      return;
    }
    this.invite.emit({ totalRounds, roundSeconds });
  }
}
