import { HttpContext, HttpXsrfTokenExtractor } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService, LoginRequest, PlayerDto, RegisterRequest } from '../api';
import { SKIP_ERROR_TOAST } from '../http/skip-error-toast';
import { RealtimeService } from '../realtime/realtime.service';

/** The caller (a form) shows these errors itself: no global toast. */
const FORM_REQUEST = { context: new HttpContext().set(SKIP_ERROR_TOAST, true) };

/**
 * Global auth state. Components read the signals; only this store changes them.
 * Errors from `login`/`register` are the raw `HttpErrorResponse` so forms can map
 * ProblemDetail field errors onto their controls.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthService);
  private readonly realtime = inject(RealtimeService);
  private readonly xsrf = inject(HttpXsrfTokenExtractor);

  private readonly _currentUser = signal<PlayerDto | null>(null);
  private sessionRestore: Promise<boolean> | null = null;

  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);

  /**
   * Restores the session from the server cookie (`GET /api/auth/me`), at most once until the
   * next `clear()`. Runs at app start and from `authGuard`. Never rejects.
   */
  restoreSession(): Promise<boolean> {
    this.sessionRestore ??= this.fetchCurrentUser();
    return this.sessionRestore;
  }

  /** Starts a session. Rejects with the `HttpErrorResponse` (401 bad credentials, 400). */
  async login(credentials: LoginRequest): Promise<PlayerDto> {
    if (this.needsCsrfToken()) {
      await this.restoreSession();
    }
    const player = await firstValueFrom(
      this.api.login({ loginRequest: credentials }, 'body', false, FORM_REQUEST),
    );
    this._currentUser.set(player);
    return player;
  }

  /** Creates an account without logging in. Rejects with the `HttpErrorResponse` (400, 409). */
  async register(request: RegisterRequest): Promise<PlayerDto> {
    if (this.needsCsrfToken()) {
      await this.restoreSession();
    }
    return firstValueFrom(
      this.api.register({ registerRequest: request }, 'body', false, FORM_REQUEST),
    );
  }

  /**
   * Closes the STOMP connection, ends the server session and forgets the player.
   * The local state is cleared even if the server call fails.
   */
  async logout(): Promise<void> {
    await this.realtime.disconnect();
    try {
      await firstValueFrom(this.api.logout('body', false, FORM_REQUEST));
    } catch {
      // The session is gone either way for this tab; nothing useful to tell the player.
    } finally {
      this.clear();
    }
  }

  /** Drops the local user, e.g. after a 401. Realtime disconnects through the effect in app.config. */
  clear(): void {
    this._currentUser.set(null);
    this.sessionRestore = null;
  }

  private async fetchCurrentUser(): Promise<boolean> {
    try {
      this._currentUser.set(await firstValueFrom(this.api.me()));
      return true;
    } catch {
      // 401 (no session) or server unreachable: start logged out.
      return false;
    }
  }

  /**
   * Spring sets the XSRF-TOKEN cookie on any response and clears it at logout. Before a mutating
   * call with no cookie (e.g. login right after logout), one `GET /me` makes the server issue it.
   */
  private needsCsrfToken(): boolean {
    return this.xsrf.getToken() === null;
  }
}
