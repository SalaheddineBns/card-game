import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { AuthStore } from '../auth/auth-store';
import { RealtimeService } from '../realtime/realtime.service';
import { DARK_CLASS } from '../theme/theme.service';
import { Layout } from './layout';

async function renderLayout() {
  return render(Layout, {
    providers: [
      provideRouter([]),
      MessageService,
      { provide: RealtimeService, useValue: { connectionState: signal('connected') } },
    ],
  });
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
    const { fixture } = await renderLayout();
    fixture.debugElement.injector.get(AuthStore).setCurrentUser({ username: 'alice' });
    fixture.detectChanges();

    expect(await screen.findByRole('button', { name: 'Menu de alice' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Salon' })[0]).toHaveAttribute('href', '/lobby');
    expect(screen.getByText('En ligne')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Connexion' })).toBeNull();
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
