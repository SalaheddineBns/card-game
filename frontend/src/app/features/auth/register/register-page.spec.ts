import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent, { UserEvent } from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { throwError } from 'rxjs';
import { AuthApiStub, authApiStub, httpError, provideAuthApi } from '../../../../testing/auth';
import { AuthStore } from '../../../core/auth/auth-store';
import { RegisterPage } from './register-page';

let api: AuthApiStub;

async function renderRegister() {
  api = authApiStub();
  await render(RegisterPage, {
    providers: [provideRouter([{ path: '**', children: [] }]), MessageService, provideAuthApi(api)],
  });
  return userEvent.setup();
}

async function fillValidForm(user: UserEvent, username = 'alice'): Promise<void> {
  await user.type(screen.getByLabelText("Nom d'utilisateur"), username);
  await user.type(screen.getByLabelText('Âge'), '30');
  await user.type(screen.getByLabelText('Mot de passe'), 'password123');
  await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'password123');
  await user.click(screen.getByLabelText("J'accepte les conditions d'utilisation"));
}

const submit = (user: UserEvent) =>
  user.click(screen.getByRole('button', { name: 'Créer mon compte' }));

describe('RegisterPage', () => {
  it('creates the account, confirms it and sends the player to login with the username filled in', async () => {
    const user = await renderRegister();
    const toast = vi.spyOn(TestBed.inject(MessageService), 'add');

    await fillValidForm(user);
    await submit(user);

    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/auth/login?username=alice'));
    expect(api.register).toHaveBeenCalledWith(
      { registerRequest: { username: 'alice', password: 'password123', age: 30, gender: null } },
      'body',
      false,
      expect.anything(),
    );
    expect(toast).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'success', summary: 'Compte créé' }),
    );
    expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(false);
  });

  it('lists every missing field and does not call the server', async () => {
    const user = await renderRegister();

    await submit(user);

    expect(screen.getByText("Le nom d'utilisateur est obligatoire.")).toBeInTheDocument();
    expect(screen.getByText("L'âge est obligatoire.")).toBeInTheDocument();
    expect(screen.getByText('Le mot de passe est obligatoire.')).toBeInTheDocument();
    expect(screen.getByText('Veuillez confirmer le mot de passe.')).toBeInTheDocument();
    expect(
      screen.getByText("Vous devez accepter les conditions d'utilisation."),
    ).toBeInTheDocument();
    expect(api.register).not.toHaveBeenCalled();
  });

  it('applies the username and password rules', async () => {
    const user = await renderRegister();
    const username = screen.getByLabelText("Nom d'utilisateur");

    await user.type(username, 'al');
    await user.tab();
    expect(
      await screen.findByText("Le nom d'utilisateur doit contenir au moins 3 caractères."),
    ).toBeInTheDocument();

    await user.clear(username);
    await user.type(username, 'alice bob');
    await user.tab();
    expect(
      await screen.findByText('Lettres, chiffres, « _ » et « - » uniquement.'),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText('Mot de passe'), 'short');
    await user.tab();
    expect(
      await screen.findByText('Le mot de passe doit contenir au moins 8 caractères.'),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'different');
    await user.tab();
    expect(await screen.findByText('Les mots de passe ne correspondent pas.')).toBeInTheDocument();
  });

  it('shows a taken username on the username field', async () => {
    const user = await renderRegister();
    api.register.mockReturnValue(
      throwError(() =>
        httpError(409, { title: 'Conflit', detail: "Nom d'utilisateur déjà pris", status: 409 }),
      ),
    );

    await fillValidForm(user, 'Alice');
    await submit(user);

    expect(await screen.findByText("Nom d'utilisateur déjà pris")).toBeInTheDocument();
    expect(screen.getByLabelText("Nom d'utilisateur")).toHaveAttribute('aria-invalid', 'true');
    expect(TestBed.inject(Router).url).toBe('/');
    expect(screen.getByRole('button', { name: 'Créer mon compte' })).toBeEnabled();
  });

  it('puts server validation errors on the matching fields', async () => {
    const user = await renderRegister();
    api.register.mockReturnValue(
      throwError(() =>
        httpError(400, {
          title: 'Requête invalide',
          detail: 'Certains champs sont invalides.',
          status: 400,
          errors: [{ field: 'password', message: 'Mot de passe trop faible' }],
        }),
      ),
    );

    await fillValidForm(user);
    await submit(user);

    expect(await screen.findByText('Mot de passe trop faible')).toBeInTheDocument();
    expect(screen.queryByText('Certains champs sont invalides.')).toBeNull();
  });
});
