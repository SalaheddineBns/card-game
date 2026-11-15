import { Browser, Page, expect, test } from '@playwright/test';

// docs/migration-plan.md rows 7-14: two players, invite → accept → play → result.
// Needs the dev seed (D13): alice / bob with password "password123" (`./mvnw spring-boot:test-run`).
const PASSWORD = 'password123';

async function loggedInPage(browser: Browser, username: string): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('/auth/login');
  await page.getByLabel("Nom d'utilisateur").fill(username);
  await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL('/lobby');
  return page;
}

test('alice invites bob, both play a 1 × 5 s game and see the result', async ({ browser }) => {
  test.setTimeout(60_000);
  const alice = await loggedInPage(browser, 'alice');
  const bob = await loggedInPage(browser, 'bob');

  // Presence: bob shows up in alice's lobby once his socket is connected.
  await alice.getByRole('checkbox', { name: 'bob' }).check({ timeout: 15_000 });
  await alice.getByLabel('Nombre de manches').fill('1');
  await alice.getByLabel('Secondes par manche').fill('5');
  await alice.getByRole('button', { name: "Envoyer l'invitation" }).click();

  const invitation = bob.getByRole('dialog', { name: 'Invitation de alice' });
  await expect(invitation).toBeVisible();
  await invitation.getByRole('button', { name: 'Accepter' }).click();

  await expect(alice).toHaveURL(/\/games\/\d+$/);
  await expect(bob).toHaveURL(/\/games\/\d+$/);
  await expect(alice.getByText('Manche 1 / 1')).toBeVisible();
  await expect(bob.getByText('Carte centrale')).toBeVisible();

  // One claim: right or wrong, the server answers (score or rejection).
  await alice.getByRole('button', { name: /Même couleur/ }).click();
  await expect(alice.getByText(/Bien joué|Raté|Trop tard|marque un point/)).toBeVisible();

  // The server ends the game when the 5 s round runs out.
  for (const page of [alice, bob]) {
    await expect(page).toHaveURL(/\/games\/\d+\/result$/, { timeout: 20_000 });
    await expect(page.getByRole('heading', { name: 'Résultat de la partie' })).toBeVisible();
    await expect(page.getByRole('row', { name: /alice/ })).toBeVisible();
    await expect(page.getByRole('row', { name: /bob/ })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Rejouer' })).toBeVisible();
  }
});
