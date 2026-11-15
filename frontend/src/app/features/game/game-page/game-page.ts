import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  numberAttribute,
} from '@angular/core';
import { PagePlaceholder } from '../../../shared/ui/page-placeholder/page-placeholder';

// TODO(api): build once the OpenAPI client exists (docs/migration-plan.md row 10-12).
@Component({
  selector: 'app-game-page',
  imports: [PagePlaceholder],
  template: `<app-page-placeholder heading="Partie" [description]="description()" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GamePage {
  /** Route param `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly description = computed(() => `Partie n° ${this.id()}`);
}
