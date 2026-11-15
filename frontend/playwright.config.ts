import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the real stack:
 * - backend on http://localhost:8080, started separately with the `local` profile
 *   (e.g. `cd backend && ./mvnw spring-boot:test-run`, which brings PostgreSQL up with Testcontainers);
 * - `ng serve` on :4300 (not 4200, so a dev server you already run is never mistaken for this one),
 *   started here, proxying /api and /ws to the backend (proxy.conf.json).
 */
const PORT = 4300;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    locale: 'fr-FR',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm start -- --port ${PORT}`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
});
