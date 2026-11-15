import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PagePlaceholder } from '../../../shared/ui/page-placeholder/page-placeholder';

// TODO(api): build once the OpenAPI client exists (docs/migration-plan.md row 7-9).
@Component({
  selector: 'app-lobby-page',
  imports: [PagePlaceholder],
  template: `<app-page-placeholder heading="Salon" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyPage {}
