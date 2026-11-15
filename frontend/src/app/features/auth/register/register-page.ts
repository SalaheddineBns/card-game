import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PagePlaceholder } from '../../../shared/ui/page-placeholder/page-placeholder';

// TODO(api): build once the OpenAPI client exists (docs/migration-plan.md row 1).
@Component({
  selector: 'app-register-page',
  imports: [PagePlaceholder],
  template: `<app-page-placeholder heading="Créer un compte" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {}
