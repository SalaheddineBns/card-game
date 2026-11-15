import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ProgressBarModule } from 'primeng/progressbar';

/** Round number and time left, from the server's round instants (no client timer decides anything). */
@Component({
  selector: 'app-round-timer',
  imports: [ProgressBarModule],
  template: `
    <div class="flex flex-col gap-2">
      <div class="flex items-baseline justify-between gap-4">
        <p class="text-lg font-semibold">Manche {{ round() }} / {{ totalRounds() }}</p>
        <p
          class="font-mono text-2xl font-bold tabular-nums"
          [class.animate-pulse]="urgent()"
          [class.text-primary]="urgent()"
        >
          <span class="sr-only">Temps restant : </span>{{ secondsLeft() }} s
        </p>
      </div>
      <p-progressbar
        [value]="percent()"
        [showValue]="false"
        [style]="{ height: '0.75rem' }"
        [attr.aria-label]="'Temps restant dans la manche'"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoundTimer {
  readonly round = input.required<number>();
  readonly totalRounds = input.required<number>();
  readonly remainingMs = input.required<number>();
  /** Full round length in ms (`roundEndsAt - roundStartedAt`). */
  readonly roundMs = input.required<number>();

  protected readonly secondsLeft = computed(() =>
    Math.max(0, Math.ceil(this.remainingMs() / 1000)),
  );
  protected readonly percent = computed(() =>
    Math.min(
      100,
      Math.max(0, this.roundMs() > 0 ? (this.remainingMs() / this.roundMs()) * 100 : 0),
    ),
  );
  protected readonly urgent = computed(() => this.secondsLeft() <= 3);
}
