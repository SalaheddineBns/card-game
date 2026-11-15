import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PagePlaceholder } from '../../../shared/ui/page-placeholder/page-placeholder';

// TODO(api): build once the OpenAPI client exists (docs/migration-plan.md row 5-6).
@Component({
  selector: 'app-profile-page',
  imports: [PagePlaceholder],
  template: `<app-page-placeholder heading="Mon profil" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {}
