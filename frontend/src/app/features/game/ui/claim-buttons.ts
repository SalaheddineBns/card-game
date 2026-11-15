import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { Claim } from '../../../core/realtime/protocol';

export interface ClaimOption {
  claim: Claim;
  label: string;
  /** Keyboard shortcut, shown on the button. */
  key: string;
  icon: string;
}

/** French labels per D21 (legacy "The Same Color", "The Same Format", "The Two"). */
export const CLAIM_OPTIONS: readonly ClaimOption[] = [
  { claim: 'SAME_COLOR', label: 'Même couleur', key: 'C', icon: 'pi pi-palette' },
  { claim: 'SAME_SYMBOL', label: 'Même forme', key: 'F', icon: 'pi pi-language' },
  { claim: 'BOTH', label: 'Les deux', key: 'D', icon: 'pi pi-bolt' },
];

@Component({
  selector: 'app-claim-buttons',
  imports: [ButtonModule],
  template: `
    <div class="grid gap-3 sm:grid-cols-3" role="group" aria-label="Annoncer">
      @for (option of options; track option.claim) {
        <p-button
          [label]="option.label"
          [icon]="option.icon"
          size="large"
          [fluid]="true"
          [disabled]="disabled()"
          [ariaLabel]="option.label + ' (touche ' + option.key + ')'"
          [attr.aria-keyshortcuts]="option.key"
          (onClick)="claim.emit(option.claim)"
        >
        </p-button>
      }
    </div>
    <p class="mt-2 text-center text-xs text-muted-color">
      Raccourcis clavier : <kbd>C</kbd> même couleur · <kbd>F</kbd> même forme · <kbd>D</kbd> les
      deux
    </p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClaimButtons {
  readonly disabled = input(false);
  readonly claim = output<Claim>();

  protected readonly options = CLAIM_OPTIONS;
}
