import { DOCUMENT } from '@angular/common';
import { HttpXsrfTokenExtractor } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { IFrame, RxStomp, RxStompState } from '@stomp/rx-stomp';
import { Observable, filter, firstValueFrom, map, tap } from 'rxjs';
import { AuthService } from '../api';
import { ServerClock } from './server-clock';
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

const HEARTBEAT_MS = 10_000;
const RECONNECT_DELAY_MS = 5_000;
/** Backoff after a broker ERROR frame: 1 s, 2 s, 4 s... capped. */
const ERROR_BACKOFF_BASE_MS = 1_000;
const ERROR_BACKOFF_MAX_MS = 30_000;
/** A connection that stays open this long resets the backoff. */
const STABLE_CONNECTION_MS = 30_000;

/**
 * Owns the single STOMP connection (docs/realtime-protocol.md). Components never touch rx-stomp:
 * they use the typed streams below. Subscriptions made before `connect()` start once connected,
 * and rx-stomp re-subscribes them after a reconnect.
 *
 * - Socket closed (network, server restart): rx-stomp reconnects every 5 s.
 * - STOMP ERROR frame (bad CSRF on CONNECT, forbidden SUBSCRIBE): Spring keeps the socket open
 *   but the session is unusable, so it is fatal: deactivate, then reconnect with backoff.
 * - The CSRF cookie is read right before every CONNECT (it rotates at login).
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly document = inject(DOCUMENT);
  private readonly xsrf = inject(HttpXsrfTokenExtractor);
  private readonly authApi = inject(AuthService);
  private readonly serverClock = inject(ServerClock);
  private readonly rxStomp = new RxStomp();

  /** True between `connect()` and `disconnect()`, including while waiting to reconnect. */
  private wanted = false;
  /** True from a broker ERROR until the delayed reconnect. */
  private restarting = false;
  private errorCount = 0;
  private restartTimer: ReturnType<typeof setTimeout> | undefined;
  private stableTimer: ReturnType<typeof setTimeout> | undefined;

  readonly connectionState = toSignal(
    this.rxStomp.connectionState$.pipe(map((state) => STATE_BY_RX_STATE[state])),
    { requireSync: true },
  );

  constructor() {
    this.rxStomp.configure({
      brokerURL: this.brokerUrl(),
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
      reconnectDelay: RECONNECT_DELAY_MS,
      beforeConnect: (client) => this.prepareConnect(client),
    });
    this.rxStomp.stompErrors$
      .pipe(takeUntilDestroyed())
      .subscribe((frame) => this.onBrokerError(frame));
    this.rxStomp.connectionState$
      .pipe(takeUntilDestroyed())
      .subscribe((state) => this.trackStability(state));
  }

  /** Opens the connection with the session cookie. Idempotent. Called once the player is logged in. */
  connect(): void {
    this.wanted = true;
    if (this.rxStomp.active || this.restarting) {
      return;
    }
    this.rxStomp.activate();
  }

  /** Closes the connection (logout, 401). Safe to call when not connected. */
  async disconnect(): Promise<void> {
    this.wanted = false;
    this.errorCount = 0;
    this.restarting = false;
    clearTimeout(this.restartTimer);
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
      tap((event) => this.observeSentAt(event)),
    );
  }

  /** Puts the current CSRF cookie in the CONNECT headers; asks the server for one if it is missing. */
  private async prepareConnect(client: RxStomp): Promise<void> {
    let token = this.xsrf.getToken();
    if (token === null) {
      try {
        await firstValueFrom(this.authApi.me());
      } catch {
        // No session: the handshake will be refused and rx-stomp retries later.
      }
      token = this.xsrf.getToken();
    }
    client.configure({ connectHeaders: token ? { [CSRF_CONNECT_HEADER]: token } : {} });
  }

  private onBrokerError(frame: IFrame): void {
    console.warn('STOMP ERROR frame, reconnecting:', frame.headers['message'] ?? frame.body);
    if (!this.wanted || this.restarting) {
      return;
    }
    const delay = Math.min(ERROR_BACKOFF_BASE_MS * 2 ** this.errorCount, ERROR_BACKOFF_MAX_MS);
    this.errorCount++;
    this.restarting = true;
    void this.rxStomp.deactivate().then(() => {
      this.restartTimer = setTimeout(() => {
        if (this.restarting && this.wanted) {
          this.restarting = false;
          this.rxStomp.activate();
        }
      }, delay);
    });
  }

  private trackStability(state: RxStompState): void {
    clearTimeout(this.stableTimer);
    if (state === RxStompState.OPEN) {
      this.stableTimer = setTimeout(() => (this.errorCount = 0), STABLE_CONNECTION_MS);
    }
  }

  private observeSentAt(event: object): void {
    const sentAt = (event as Record<string, unknown>)['sentAt'];
    if (typeof sentAt === 'string') {
      this.serverClock.observe(sentAt);
    }
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
