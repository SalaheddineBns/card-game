import { DOCUMENT } from '@angular/common';
import { HttpXsrfTokenExtractor } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RxStomp, RxStompState } from '@stomp/rx-stomp';
import { Observable, filter, map } from 'rxjs';
import {
  CSRF_CONNECT_HEADER,
  ClaimCommand,
  Destinations,
  GAME_EVENT_TYPES,
  GAME_LIFECYCLE_EVENT_TYPES,
  GameEvent,
  GameLifecycleEvent,
  INVITATION_EVENT_TYPES,
  InvitationEvent,
  PRESENCE_EVENT_TYPES,
  PresenceEvent,
  USER_ERROR_EVENT_TYPES,
  UserErrorEvent,
  WS_ENDPOINT,
} from './protocol';

export type ConnectionState = 'connecting' | 'connected' | 'disconnecting' | 'disconnected';

const STATE_BY_RX_STATE: Record<RxStompState, ConnectionState> = {
  [RxStompState.CONNECTING]: 'connecting',
  [RxStompState.OPEN]: 'connected',
  [RxStompState.CLOSING]: 'disconnecting',
  [RxStompState.CLOSED]: 'disconnected',
};

/**
 * Owns the single STOMP connection (docs/realtime-protocol.md). Components never touch rx-stomp:
 * they use the typed streams below. Subscriptions made before `connect()` start once connected,
 * and rx-stomp re-subscribes them after a reconnect.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly document = inject(DOCUMENT);
  private readonly xsrf = inject(HttpXsrfTokenExtractor);
  private readonly rxStomp = new RxStomp();

  readonly connectionState = toSignal(
    this.rxStomp.connectionState$.pipe(map((state) => STATE_BY_RX_STATE[state])),
    { requireSync: true },
  );

  /** Opens the connection with the session cookie. Idempotent. Called once the player is logged in. */
  connect(): void {
    if (this.rxStomp.active) {
      return;
    }
    this.rxStomp.configure({
      brokerURL: this.brokerUrl(),
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: 5_000,
      // Read the CSRF cookie on every (re)connect: Spring may rotate it after login.
      beforeConnect: (client) => {
        const token = this.xsrf.getToken();
        client.configure({ connectHeaders: token ? { [CSRF_CONNECT_HEADER]: token } : {} });
      },
    });
    this.rxStomp.activate();
  }

  /** Closes the connection (logout, 401). Safe to call when not connected. */
  async disconnect(): Promise<void> {
    if (this.rxStomp.active) {
      await this.rxStomp.deactivate();
    }
  }

  presence(): Observable<PresenceEvent> {
    return this.watch<PresenceEvent>(Destinations.presence, PRESENCE_EVENT_TYPES);
  }

  invitations(): Observable<InvitationEvent> {
    return this.watch<InvitationEvent>(Destinations.invitations, INVITATION_EVENT_TYPES);
  }

  gameLifecycle(): Observable<GameLifecycleEvent> {
    return this.watch<GameLifecycleEvent>(Destinations.games, GAME_LIFECYCLE_EVENT_TYPES);
  }

  errors(): Observable<UserErrorEvent> {
    return this.watch<UserErrorEvent>(Destinations.errors, USER_ERROR_EVENT_TYPES);
  }

  watchGame(gameId: number): Observable<GameEvent> {
    return this.watch<GameEvent>(Destinations.game(gameId), GAME_EVENT_TYPES);
  }

  sendClaim(gameId: number, command: ClaimCommand): void {
    this.rxStomp.publish({
      destination: Destinations.claim(gameId),
      body: JSON.stringify(command),
    });
  }

  private watch<E extends { type: string }>(
    destination: string,
    types: readonly E['type'][],
  ): Observable<E> {
    return this.rxStomp.watch({ destination }).pipe(
      map((message) => parseJson(message.body)),
      filter((payload): payload is E => hasType(payload, types)),
    );
  }

  private brokerUrl(): string {
    const { protocol, host } = this.document.location;
    return `${protocol === 'https:' ? 'wss:' : 'ws:'}//${host}${WS_ENDPOINT}`;
  }
}

function parseJson(body: string): unknown {
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return null;
  }
}

/** Trust boundary: the server owns the shape; we only check the discriminator. */
function hasType(payload: unknown, types: readonly string[]): boolean {
  if (typeof payload !== 'object' || payload === null) {
    return false;
  }
  const type = (payload as Record<string, unknown>)['type'];
  return typeof type === 'string' && types.includes(type);
}
