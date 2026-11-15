import { Provider, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import {
  GameEvent,
  GameLifecycleEvent,
  InvitationEvent,
  PresenceEvent,
  UserErrorEvent,
} from '../app/core/realtime/protocol';
import { ConnectionState, RealtimeService } from '../app/core/realtime/realtime.service';

/** Test-only stand-in for `RealtimeService`: push events through the Subjects. */
export function fakeRealtime() {
  const presence = new Subject<PresenceEvent>();
  const invitations = new Subject<InvitationEvent>();
  const games = new Subject<GameLifecycleEvent>();
  const errors = new Subject<UserErrorEvent>();
  const game = new Subject<GameEvent>();
  return {
    presence$: presence,
    invitations$: invitations,
    games$: games,
    errors$: errors,
    game$: game,
    connectionState: signal<ConnectionState>('connected'),
    connect: vi.fn(),
    disconnect: vi.fn(async () => undefined),
    presence: () => presence.asObservable(),
    invitations: () => invitations.asObservable(),
    gameLifecycle: () => games.asObservable(),
    errors: () => errors.asObservable(),
    watchGame: vi.fn<(gameId: number) => Observable<GameEvent>>(() => game.asObservable()),
    sendClaim: vi.fn(),
  };
}

export type FakeRealtime = ReturnType<typeof fakeRealtime>;

export function provideFakeRealtime(fake: FakeRealtime): Provider {
  return { provide: RealtimeService, useValue: fake };
}

export const SENT_AT = '2026-11-15T20:00:00Z';
