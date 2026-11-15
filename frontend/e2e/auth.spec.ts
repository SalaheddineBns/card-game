import { expect, test } from '@playwright/test';

// docs/migration-plan.md rows 1-3: register → login → lobby → logout.
test('a new player registers, logs in, lands on the lobby and logs out', async ({ page }) => {
  // Unique per run: usernames are unique and the database outlives the test.
  const username = `e2e_${Date.now().toString(36)}`;
  const password = 'motdepasse-e2e';

  await page.goto('/auth/register');
  await page.getByLabel("Nom d'utilisateur").fill(username);
  await page.getByLabel('Âge').fill('25');
  await page.getByLabel('Mot de passe', { exact: true }).fill(password);
  await page.getByLabel('Confirmer le mot de passe').fill(password);
  await page.getByText("J'accepte les conditions d'utilisation").click();
  await page.getByRole('button', { name: 'Créer mon compte' }).click();

  await expect(page.getByText('Compte créé')).toBeVisible();
  await expect(page).toHaveURL(/\/auth\/login\?username=/);
  await expect(page.getByLabel("Nom d'utilisateur")).toHaveValue(username);

  await page.getByLabel('Mot de passe', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  await expect(page).toHaveURL('/lobby');
  await expect(page.getByRole('heading', { name: 'Salon' })).toBeVisible();
  const menu = page.getByRole('button', { name: `Menu de ${username}` });
  await expect(menu).toBeVisible();

  await menu.click();
  await page.getByRole('menuitem', { name: 'Déconnexion' }).click();

  await expect(page).toHaveURL('/');
  await expect(page.getByRole('link', { name: 'Connexion' })).toBeVisible();

  // The session is really gone: the lobby sends us back to login.
  await page.goto('/lobby');
  await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Flobby/);
});
