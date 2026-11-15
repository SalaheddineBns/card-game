import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import { ALICE, provideAuthApi, signIn } from '../../../testing/auth';
import { LandingPage } from './landing-page';

describe('LandingPage', () => {
  it('invites a visitor to play through the login page or to sign up', async () => {
    await render(LandingPage, { providers: [provideRouter([]), provideAuthApi(null)] });

    expect(screen.getByRole('heading', { level: 1, name: 'Speedest Game' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Jouer' })).toHaveAttribute('href', '/auth/login');
    expect(screen.getByRole('link', { name: 'Créer un compte' })).toHaveAttribute(
      'href',
      '/auth/register',
    );
  });

  it('sends a logged-in player straight to the lobby', async () => {
    const { fixture } = await render(LandingPage, {
      providers: [provideRouter([]), provideAuthApi(ALICE)],
    });
    await signIn();
    fixture.detectChanges();

    expect(await screen.findByRole('link', { name: 'Jouer' })).toHaveAttribute('href', '/lobby');
    expect(screen.queryByRole('link', { name: 'Créer un compte' })).toBeNull();
  });
});
