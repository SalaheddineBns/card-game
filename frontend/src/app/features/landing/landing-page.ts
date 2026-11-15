import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { AuthStore } from '../../core/auth/auth-store';
import { Card } from '../../core/realtime/protocol';
import { PlayingCard } from '../../shared/ui/playing-card/playing-card';

interface Rule {
  id: string;
  icon: string;
  title: string;
  text: string;
}

/** Public landing page (legacy index.jsp `main`). "Jouer" opens the login page, or the lobby when logged in. */
@Component({
  selector: 'app-landing-page',
  imports: [RouterLink, ButtonModule, PlayingCard],
  templateUrl: './landing-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingPage {
  private readonly auth = inject(AuthStore);

  protected readonly isAuthenticated = this.auth.isAuthenticated;
  protected readonly playLink = computed(() => (this.isAuthenticated() ? '/lobby' : '/auth/login'));

  /** Decorative hand: the center card matches the middle card on letter and colour. */
  protected readonly hand: readonly (Card & { id: string; tilt: string })[] = [
    { id: 'left', symbol: 'K', color: 'red', tilt: '-rotate-12 translate-y-3' },
    { id: 'middle', symbol: 'B', color: 'blue', tilt: 'z-10 -translate-y-2' },
    { id: 'right', symbol: 'E', color: 'green', tilt: 'rotate-12 translate-y-3' },
  ];

  protected readonly rules: readonly Rule[] = [
    {
      id: 'color',
      icon: 'pi pi-palette',
      title: 'Même couleur',
      text: 'Ta carte a la même couleur que la carte centrale ? Appuie le premier.',
    },
    {
      id: 'symbol',
      icon: 'pi pi-language',
      title: 'Même forme',
      text: 'Même lettre que la carte centrale ? Un point de plus pour toi.',
    },
    {
      id: 'both',
      icon: 'pi pi-bolt',
      title: 'Les deux',
      text: 'Lettre et couleur identiques : sois le plus rapide pour marquer.',
    },
  ];
}
