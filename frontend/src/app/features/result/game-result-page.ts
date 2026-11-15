import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { AuthStore } from '../../core/auth/auth-store';
import { GameResultService } from './game-result.service';

interface ScoreRow {
  player: string;
  score: number;
  isMe: boolean;
}

/**
 * Result page (legacy WinnerPage.jsp): "Gagnant" / "Perdant" lines (D20), "Égalité" for a draw
 * with no "Perdant" line when there is no single loser (D3/D4), then every score.
 */
@Component({
  selector: 'app-game-result-page',
  imports: [RouterLink, ButtonModule, SkeletonModule],
  templateUrl: './game-result-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameResultPage {
  private readonly results = inject(GameResultService);
  private readonly auth = inject(AuthStore);

  /** Route param `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly result = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.results.load(params),
  });

  protected readonly view = computed(() => (this.result.hasValue() ? this.result.value() : null));

  protected readonly leadersLabel = computed(() => this.view()?.leaders.join(', ') ?? '');

  protected readonly rows = computed<ScoreRow[]>(() => {
    const me = this.auth.currentUser()?.username.toLowerCase();
    return [...(this.view()?.scores ?? [])]
      .sort((a, b) => b.score - a.score)
      .map((s) => ({ ...s, isMe: s.player.toLowerCase() === me }));
  });

  protected readonly outcome = computed(() => {
    const view = this.view();
    const me = this.auth.currentUser()?.username.toLowerCase();
    if (!view || !me) {
      return null;
    }
    if (view.winner?.toLowerCase() === me) {
      return 'Bravo, vous avez gagné !';
    }
    if (view.draw && view.leaders.some((l) => l.toLowerCase() === me)) {
      return 'Vous partagez la première place.';
    }
    return null;
  });
}
