import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PagePlaceholder } from '../../../shared/ui/page-placeholder/page-placeholder';

// TODO(api): build once the OpenAPI client exists (docs/migration-plan.md row 2).
@Component({
  selector: 'app-login-page',
  imports: [PagePlaceholder],
  template: `<app-page-placeholder heading="Connexion" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {}
