import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { render, screen, within } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { ALICE, AuthApiStub, authApiStub, provideAuthApi, signIn } from '../../../testing/auth';
import { PlayerDto } from '../api';
import { RealtimeService } from '../realtime/realtime.service';
import { DARK_CLASS } from '../theme/theme.service';
import { Layout } from './layout';

let api: AuthApiStub;
let realtime: {
  connectionState: ReturnType<typeof signal<string>>;
  disconnect: () => Promise<void>;
};

async function renderLayout(sessionPlayer: PlayerDto | null = null) {
  api = authApiStub(sessionPlayer);
  realtime = { connectionState: signal('connected'), disconnect: vi.fn(async () => undefined) };
  const result = await render(Layout, {
    providers: [
      provideRouter([{ path: '**', children: [] }]),
      MessageService,
      provideAuthApi(api),
      { provide: RealtimeService, useValue: realtime },
    ],
  });
  if (sessionPlayer) {
    await signIn();
    result.fixture.detectChanges();
  }
  return result;
}

describe('Layout', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove(DARK_CLASS);
  });

  it('shows the app name, the home link and guest actions when logged out', async () => {
    await renderLayout();

    expect(screen.getByRole('link', { name: /speedest game/i })).toHaveAttribute('href', '/');
    expect(screen.getAllByRole('link', { name: 'Accueil' }).length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Connexion' })).toHaveAttribute('href', '/auth/login');
    expect(screen.queryByRole('link', { name: 'Salon' })).toBeNull();
  });

  it('shows the player menu, lobby link and connection state when logged in', async () => {
    await renderLayout(ALICE);

    expect(await screen.findByRole('button', { name: 'Menu de alice' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Salon' })[0]).toHaveAttribute('href', '/lobby');
    expect(screen.getByText('En ligne')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Connexion' })).toBeNull();
  });

  it('logs out from the player menu: server logout, realtime closed, back to the home page', async () => {
    await renderLayout(ALICE);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/lobby');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Menu de alice' }));
    const logoutItem = await screen.findByRole('menuitem', { name: 'Déconnexion' });
    // PrimeNG listens on the item's content, not on the role="menuitem" element itself.
    await user.click(within(logoutItem).getByText('Déconnexion'));

    expect(realtime.disconnect).toHaveBeenCalled();
    expect(api.logout).toHaveBeenCalledOnce();
    expect(await screen.findByRole('link', { name: 'Connexion' })).toBeInTheDocument();
    expect(router.url).toBe('/');
  });

  it('toggles dark mode on <html> and remembers it', async () => {
    await renderLayout();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Activer le mode sombre' }));

    expect(document.documentElement).toHaveClass(DARK_CLASS);
    expect(localStorage.getItem('card-game.theme')).toBe('dark');
    expect(screen.getByRole('button', { name: 'Activer le mode clair' })).toBeInTheDocument();
  });
});
