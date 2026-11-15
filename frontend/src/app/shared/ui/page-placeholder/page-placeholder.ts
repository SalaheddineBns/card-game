import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Temporary body for screens waiting on the backend API. */
@Component({
  selector: 'app-page-placeholder',
  template: `
    <section class="mx-auto max-w-3xl px-4 py-16">
      <h1 class="text-3xl font-bold">{{ heading() }}</h1>
      <p class="mt-4 text-muted-color">{{ description() }}</p>
      <ng-content />
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PagePlaceholder {
  readonly heading = input.required<string>();
  readonly description = input('Cette page arrive bientôt.');
}
