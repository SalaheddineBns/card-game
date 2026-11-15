import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  numberAttribute,
} from '@angular/core';
import { PagePlaceholder } from '../../../shared/ui/page-placeholder/page-placeholder';

// TODO(api): build once the OpenAPI client exists (docs/migration-plan.md row 14).
@Component({
  selector: 'app-game-result-page',
  imports: [PagePlaceholder],
  template: `<app-page-placeholder heading="Résultat" [description]="description()" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameResultPage {
  /** Route param `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly description = computed(() => `Résultat de la partie n° ${this.id()}`);
}
