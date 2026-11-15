import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { throwError } from 'rxjs';
import {
  ALICE,
  AuthApiStub,
  authApiStub,
  httpError,
  provideAuthApi,
} from '../../../../testing/auth';
import { AuthStore } from '../../../core/auth/auth-store';
import { LoginPage } from './login-page';

let api: AuthApiStub;

async function renderLogin(inputs: { returnUrl?: string; username?: string } = {}) {
  api = authApiStub();
  await render(LoginPage, {
    inputs,
    providers: [provideRouter([{ path: '**', children: [] }]), MessageService, provideAuthApi(api)],
  });
  return userEvent.setup();
}

describe('LoginPage', () => {
  it('logs the player in and goes to the page they came from', async () => {
    const user = await renderLogin({ returnUrl: '/games/7' });

    await user.type(screen.getByLabelText("Nom d'utilisateur"), 'alice');
    await user.type(screen.getByLabelText('Mot de passe'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/games/7'));
    expect(api.login).toHaveBeenCalledWith(
      { loginRequest: { username: 'alice', password: 'password123' } },
      'body',
      false,
      expect.anything(),
    );
    expect(TestBed.inject(AuthStore).currentUser()).toEqual(ALICE);
  });

  it('goes to the lobby by default and ignores a return URL to another site', async () => {
    const user = await renderLogin({ returnUrl: '//evil.example' });

    await user.type(screen.getByLabelText("Nom d'utilisateur"), 'alice');
    await user.type(screen.getByLabelText('Mot de passe'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/lobby'));
  });

  it('shows the server message and stays on the page when the credentials are wrong', async () => {
    const user = await renderLogin();
    api.login.mockReturnValue(
      throwError(() =>
        httpError(401, {
          title: 'Échec de la connexion',
          detail: "Nom d'utilisateur ou mot de passe invalide.",
          status: 401,
        }),
      ),
    );

    await user.type(screen.getByLabelText("Nom d'utilisateur"), 'alice');
    await user.type(screen.getByLabelText('Mot de passe'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Nom d'utilisateur ou mot de passe invalide.",
    );
    expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/');
    expect(screen.getByRole('button', { name: 'Se connecter' })).toBeEnabled();
  });

  it('asks for both fields before calling the server', async () => {
    const user = await renderLogin();

    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(screen.getByText("Le nom d'utilisateur est obligatoire.")).toBeInTheDocument();
    expect(screen.getByText('Le mot de passe est obligatoire.')).toBeInTheDocument();
    expect(api.login).not.toHaveBeenCalled();
  });

  it('prefills the username after registration', async () => {
    await renderLogin({ username: 'alice' });

    expect(screen.getByLabelText("Nom d'utilisateur")).toHaveValue('alice');
  });
});
