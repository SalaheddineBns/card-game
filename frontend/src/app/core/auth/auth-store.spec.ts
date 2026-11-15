import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ALICE } from '../../../testing/auth';
import { provideApi } from '../api';
import { RealtimeService } from '../realtime/realtime.service';
import { AuthStore } from './auth-store';

const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

function setXsrfCookie(value: string | null): void {
  document.cookie =
    value === null
      ? 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      : `XSRF-TOKEN=${value}; path=/`;
}

describe('AuthStore', () => {
  let store: AuthStore;
  let backend: HttpTestingController;
  let realtime: { disconnect: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    setXsrfCookie('token-1');
    realtime = { disconnect: vi.fn(async () => undefined) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        { provide: RealtimeService, useValue: realtime },
      ],
    });
    store = TestBed.inject(AuthStore);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    setXsrfCookie(null);
  });

  describe('restoreSession', () => {
    it('loads the player of an existing server session', async () => {
      const restored = store.restoreSession();
      backend.expectOne({ method: 'GET', url: '/api/auth/me' }).flush(ALICE);

      expect(await restored).toBe(true);
      expect(store.currentUser()).toEqual(ALICE);
      expect(store.isAuthenticated()).toBe(true);
    });

    it('starts logged out when there is no session, asking the server only once', async () => {
      const first = store.restoreSession();
      const second = store.restoreSession();
      backend.expectOne('/api/auth/me').flush(null, UNAUTHORIZED);

      expect(await first).toBe(false);
      expect(await second).toBe(false);
      expect(store.currentUser()).toBeNull();
    });
  });

  describe('login', () => {
    it('posts the credentials and remembers the player', async () => {
      const login = store.login({ username: 'alice', password: 'password123' });

      const request = backend.expectOne({ method: 'POST', url: '/api/auth/login' });
      expect(request.request.body).toEqual({ username: 'alice', password: 'password123' });
      request.flush(ALICE);

      expect(await login).toEqual(ALICE);
      expect(store.currentUser()).toEqual(ALICE);
    });

    it('rejects with the server error and stays logged out on bad credentials', async () => {
      const login = store.login({ username: 'alice', password: 'wrong-password' });
      backend
        .expectOne('/api/auth/login')
        .flush({ title: 'Échec de la connexion', status: 401 }, UNAUTHORIZED);

      await expect(login).rejects.toMatchObject({ status: 401 });
      expect(store.isAuthenticated()).toBe(false);
    });

    it('first gets a CSRF cookie from the server when there is none (e.g. right after logout)', async () => {
      setXsrfCookie(null);
      const login = store.login({ username: 'alice', password: 'password123' });

      backend.expectOne('/api/auth/me').flush(null, UNAUTHORIZED);
      await vi.waitFor(() => backend.expectOne('/api/auth/login').flush(ALICE));

      expect(await login).toEqual(ALICE);
    });
  });

  describe('register', () => {
    it('creates the account without logging in', async () => {
      const request = { username: 'alice', password: 'password123', age: 30, gender: null };
      const register = store.register(request);

      const call = backend.expectOne({ method: 'POST', url: '/api/auth/register' });
      expect(call.request.body).toEqual(request);
      call.flush(ALICE, { status: 201, statusText: 'Created' });

      expect(await register).toEqual(ALICE);
      expect(store.isAuthenticated()).toBe(false);
    });
  });

  describe('logout', () => {
    async function loggedIn(): Promise<void> {
      const login = store.login({ username: 'alice', password: 'password123' });
      backend.expectOne('/api/auth/login').flush(ALICE);
      await login;
    }

    it('closes the realtime connection, ends the server session and forgets the player', async () => {
      await loggedIn();

      const logout = store.logout();
      await vi.waitFor(() =>
        backend
          .expectOne({ method: 'POST', url: '/api/auth/logout' })
          .flush(null, { status: 204, statusText: 'No Content' }),
      );
      await logout;

      expect(realtime.disconnect).toHaveBeenCalledOnce();
      expect(store.currentUser()).toBeNull();
    });

    it('forgets the player even when the server call fails', async () => {
      await loggedIn();

      const logout = store.logout();
      await vi.waitFor(() =>
        backend.expectOne('/api/auth/logout').flush(null, { status: 0, statusText: 'Unknown' }),
      );
      await logout;

      expect(store.isAuthenticated()).toBe(false);
    });
  });
});
