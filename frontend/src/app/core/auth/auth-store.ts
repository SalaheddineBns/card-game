import { Injectable, computed, signal } from '@angular/core';

/**
 * The logged-in player.
 * TODO(api): replace with the generated `PlayerDto` once `npm run api:generate` has run.
 */
export interface CurrentUser {
  username: string;
}

/** Global auth state. Components read signals; only this store changes them. */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _currentUser = signal<CurrentUser | null>(null);
  private sessionRestore: Promise<boolean> | null = null;

  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);

  /** Restores the session from the server cookie, at most once until the next `clear()` (used by `authGuard`). */
  restoreSession(): Promise<boolean> {
    this.sessionRestore ??= this.fetchCurrentUser();
    return this.sessionRestore;
  }

  /** TODO(api): call the generated `POST /api/auth/login`, then `setCurrentUser(dto)`. */
  setCurrentUser(user: CurrentUser): void {
    this._currentUser.set(user);
  }

  /** TODO(api): call the generated `POST /api/auth/logout` before clearing. */
  async logout(): Promise<void> {
    this.clear();
  }

  /** Drops the local user, e.g. after a 401. Realtime disconnects through the effect in app.config. */
  clear(): void {
    this._currentUser.set(null);
    this.sessionRestore = null;
  }

  /** TODO(api): call the generated `GET /api/auth/me`; set the user on 200, return false on 401. */
  private async fetchCurrentUser(): Promise<boolean> {
    return this.isAuthenticated();
  }
}
