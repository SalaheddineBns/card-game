/**
 * STOMP message types. Mirrors docs/realtime-protocol.md exactly (status: PROPOSAL).
 * When the backend changes a shape there, change it here. Discriminated on `type`.
 */

// ---------------------------------------------------------------------------
// Connection constants
// ---------------------------------------------------------------------------

export const WS_ENDPOINT = '/ws';
export const CSRF_CONNECT_HEADER = 'X-XSRF-TOKEN';

export const Destinations = {
  presence: '/topic/presence',
  invitations: '/user/queue/invitations',
  games: '/user/queue/games',
  errors: '/user/queue/errors',
  game: (gameId: number) => `/topic/game/${gameId}`,
  claim: (gameId: number) => `/app/game/${gameId}/claim`,
} as const;

// ---------------------------------------------------------------------------
// Shared value types
// ---------------------------------------------------------------------------

/** ISO-8601 instant, e.g. "2026-11-15T10:00:00Z". */
export type IsoInstant = string;

/** Wire values stay lower-case like legacy (`CardColor.wireValue()`). */
export type CardColor = 'red' | 'blue' | 'green';

/** The 11 legacy letters, no G (D1). */
export type CardSymbol = 'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'H' | 'I' | 'J' | 'K' | 'L';

export interface Card {
  symbol: CardSymbol;
  color: CardColor;
}

export interface PlayerCard extends Card {
  player: string;
}

export interface Deal {
  /** Starts at 1, +1 on every new deal (correct claim or new round). */
  dealVersion: number;
  playerCards: PlayerCard[];
  centerCard: Card;
}

/** Scores are a list in player order, not an object. */
export interface PlayerScore {
  player: string;
  score: number;
}

/** D10: totalRounds 1-20 (default 3), roundSeconds 5-120 (default 10). No combinations (D9). */
export interface GameSettings {
  totalRounds: number;
  roundSeconds: number;
}

export type PlayerStatus = 'ONLINE' | 'IN_GAME';
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';
export type Claim = 'SAME_COLOR' | 'SAME_SYMBOL' | 'BOTH';
export type ClaimRejectionReason = 'WRONG' | 'STALE';

interface EventBase {
  sentAt: IsoInstant;
}

// ---------------------------------------------------------------------------
// /topic/presence
// ---------------------------------------------------------------------------

export interface PlayerOnline extends EventBase {
  type: 'PlayerOnline';
  username: string;
}

export interface PlayerOffline extends EventBase {
  type: 'PlayerOffline';
  username: string;
}

/** D16: a player in a running game is shown IN_GAME. */
export interface PlayerStatusChanged extends EventBase {
  type: 'PlayerStatusChanged';
  username: string;
  status: PlayerStatus;
}

export type PresenceEvent = PlayerOnline | PlayerOffline | PlayerStatusChanged;

// ---------------------------------------------------------------------------
// /user/queue/invitations
// ---------------------------------------------------------------------------

export interface InvitationReceived extends EventBase {
  type: 'InvitationReceived';
  invitationId: number;
  inviter: string;
  invitees: string[];
  settings: GameSettings;
  /** createdAt + 60 s (D7). */
  expiresAt: IsoInstant;
}

/**
 * Sent to the inviter and every invitee on each change (D7).
 * PENDING: still waiting; ACCEPTED: GameStarted follows; DECLINED: cancelled (declinedBy set);
 * EXPIRED: 60 s without all acceptances.
 */
export interface InvitationUpdated extends EventBase {
  type: 'InvitationUpdated';
  invitationId: number;
  status: InvitationStatus;
  accepted: string[];
  declinedBy: string | null;
}

export type InvitationEvent = InvitationReceived | InvitationUpdated;

// ---------------------------------------------------------------------------
// /user/queue/games
// ---------------------------------------------------------------------------

export interface GameStarted extends EventBase {
  type: 'GameStarted';
  gameId: number;
}

export type GameLifecycleEvent = GameStarted;

// ---------------------------------------------------------------------------
// /topic/game/{gameId}
// ---------------------------------------------------------------------------

export interface ScoresUpdated extends EventBase {
  type: 'ScoresUpdated';
  gameId: number;
  scoredBy: string;
  scores: PlayerScore[];
  deal: Deal;
}

export interface RoundStarted extends EventBase {
  type: 'RoundStarted';
  gameId: number;
  round: number;
  totalRounds: number;
  roundEndsAt: IsoInstant;
  scores: PlayerScore[];
  deal: Deal;
}

/** D3/D4/D5: winner/loser are null when there is no single one; draw === (winner === null). */
export interface GameFinished extends EventBase {
  type: 'GameFinished';
  gameId: number;
  draw: boolean;
  winner: string | null;
  loser: string | null;
  leaders: string[];
  topScore: number;
  /** Empty when every score is equal. */
  trailers: string[];
  bottomScore: number;
  scores: PlayerScore[];
}

export type GameEvent = ScoresUpdated | RoundStarted | GameFinished;

// ---------------------------------------------------------------------------
// /user/queue/errors
// ---------------------------------------------------------------------------

export interface ClaimRejected extends EventBase {
  type: 'ClaimRejected';
  gameId: number;
  reason: ClaimRejectionReason;
  currentDealVersion: number;
}

export interface ErrorEvent extends EventBase {
  type: 'Error';
  status: number;
  title: string;
  detail: string;
}

export type UserErrorEvent = ClaimRejected | ErrorEvent;

// ---------------------------------------------------------------------------
// Client sends
// ---------------------------------------------------------------------------

/** SEND /app/game/{gameId}/claim */
export interface ClaimCommand {
  claim: Claim;
  dealVersion: number;
}

// ---------------------------------------------------------------------------
// Runtime discriminators (used to narrow parsed JSON)
// ---------------------------------------------------------------------------

export const PRESENCE_EVENT_TYPES = [
  'PlayerOnline',
  'PlayerOffline',
  'PlayerStatusChanged',
] as const satisfies readonly PresenceEvent['type'][];
export const INVITATION_EVENT_TYPES = [
  'InvitationReceived',
  'InvitationUpdated',
] as const satisfies readonly InvitationEvent['type'][];
export const GAME_LIFECYCLE_EVENT_TYPES = [
  'GameStarted',
] as const satisfies readonly GameLifecycleEvent['type'][];
export const GAME_EVENT_TYPES = [
  'ScoresUpdated',
  'RoundStarted',
  'GameFinished',
] as const satisfies readonly GameEvent['type'][];
export const USER_ERROR_EVENT_TYPES = [
  'ClaimRejected',
  'Error',
] as const satisfies readonly UserErrorEvent['type'][];
