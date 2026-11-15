import { HttpErrorResponse } from '@angular/common/http';
import { Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { AuthService, PlayerDto } from '../app/core/api';
import { AuthStore } from '../app/core/auth/auth-store';

/** Test-only helpers (excluded from the app build). */

export const ALICE: PlayerDto = { id: 1, username: 'alice', age: 30, gender: null };

export function httpError(status: number, body: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body, statusText: String(status) });
}

/** A stand-in for the generated `AuthService`; `me` answers with `sessionPlayer`, or 401. */
export function authApiStub(sessionPlayer: PlayerDto | null = null) {
  return {
    me: vi.fn((): Observable<PlayerDto> =>
      sessionPlayer ? of(sessionPlayer) : throwError(() => httpError(401)),
    ),
    login: vi.fn((): Observable<PlayerDto> => of(ALICE)),
    register: vi.fn((): Observable<PlayerDto> => of(ALICE)),
    logout: vi.fn((): Observable<unknown> => of(null)),
  };
}

export type AuthApiStub = ReturnType<typeof authApiStub>;

export function provideAuthApi(stub: AuthApiStub | PlayerDto | null): Provider {
  const useValue = stub !== null && 'me' in stub ? stub : authApiStub(stub);
  return { provide: AuthService, useValue };
}

/** Restores the stubbed server session through the public API. */
export async function signIn(): Promise<void> {
  await TestBed.inject(AuthStore).restoreSession();
}
