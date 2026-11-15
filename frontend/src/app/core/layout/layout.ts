import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { AuthStore } from '../auth/auth-store';
import { ConnectionState, RealtimeService } from '../realtime/realtime.service';
import { ThemeService } from '../theme/theme.service';

interface NavLink {
  path: string;
  label: string;
  exact: boolean;
}

const CONNECTION_LABELS: Record<
  ConnectionState,
  { label: string; severity: 'success' | 'warn' | 'secondary' }
> = {
  connected: { label: 'En ligne', severity: 'success' },
  connecting: { label: 'Connexion…', severity: 'warn' },
  disconnecting: { label: 'Déconnexion…', severity: 'secondary' },
  disconnected: { label: 'Hors ligne', severity: 'secondary' },
};

/** App shell: top bar, toast outlet and the routed page. */
@Component({
  selector: 'app-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ButtonModule,
    MenuModule,
    AvatarModule,
    TagModule,
    ToastModule,
  ],
  templateUrl: './layout.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Layout {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly theme = inject(ThemeService);
  private readonly realtime = inject(RealtimeService);

  protected readonly currentUser = this.auth.currentUser;
  protected readonly isAuthenticated = this.auth.isAuthenticated;
  protected readonly dark = this.theme.dark;

  protected readonly themeToggleLabel = computed(() =>
    this.dark() ? 'Activer le mode clair' : 'Activer le mode sombre',
  );
  protected readonly themeToggleIcon = computed(() => (this.dark() ? 'pi pi-sun' : 'pi pi-moon'));

  protected readonly connection = computed(
    () => CONNECTION_LABELS[this.realtime.connectionState()],
  );

  protected readonly userInitial = computed(
    () => this.currentUser()?.username.charAt(0).toUpperCase() ?? '',
  );

  protected readonly navLinks = computed<NavLink[]>(() => [
    { path: '/', label: 'Accueil', exact: true },
    ...(this.isAuthenticated()
      ? [
          { path: '/lobby', label: 'Salon', exact: false },
          { path: '/profile', label: 'Profil', exact: false },
        ]
      : []),
  ]);

  protected readonly userMenu: MenuItem[] = [
    { label: 'Mon profil', icon: 'pi pi-user', routerLink: '/profile' },
    { separator: true },
    { label: 'Déconnexion', icon: 'pi pi-sign-out', command: () => void this.logout() },
  ];

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  private async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigateByUrl('/');
  }
}
