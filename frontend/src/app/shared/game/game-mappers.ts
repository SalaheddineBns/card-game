import {
  CardDto,
  DealDto,
  GameResultDto,
  GameSettingsDto,
  GameSnapshotDto,
  InvitationDto,
  PlayerScoreDto,
} from '../../core/api';
import {
  Card,
  CardColor,
  CardSymbol,
  Deal,
  GameSettings,
  InvitationStatus,
  PlayerScore,
} from '../../core/realtime/protocol';

/*
 * The generated DTOs mark most fields optional (the backend spec has no `required` lists), while the
 * STOMP events are strict. These mappers turn REST DTOs into the same strict types as the events,
 * so features only deal with one model.
 */

const SYMBOLS: readonly string[] = ['A', 'B', 'C', 'D', 'E', 'F', 'H', 'I', 'J', 'K', 'L'];
const COLORS: readonly string[] = ['red', 'blue', 'green'];

export function isCardSymbol(value: unknown): value is CardSymbol {
  return typeof value === 'string' && SYMBOLS.includes(value);
}

export function isCardColor(value: unknown): value is CardColor {
  return typeof value === 'string' && COLORS.includes(value);
}

export function toCard(dto: CardDto | undefined): Card {
  if (!isCardSymbol(dto?.symbol) || !isCardColor(dto?.color)) {
    throw new Error(`Invalid card from server: ${JSON.stringify(dto)}`);
  }
  return { symbol: dto.symbol, color: dto.color };
}

export function toDeal(dto: DealDto | undefined): Deal {
  return {
    dealVersion: dto?.dealVersion ?? 0,
    playerCards: (dto?.playerCards ?? []).map((pc) => ({ player: pc.player ?? '', ...toCard(pc) })),
    centerCard: toCard(dto?.centerCard),
  };
}

export function toScores(dtos: PlayerScoreDto[] | undefined): PlayerScore[] {
  return (dtos ?? []).map((s) => ({ player: s.player ?? '', score: s.score ?? 0 }));
}

export function toSettings(dto: GameSettingsDto | undefined): GameSettings {
  return { totalRounds: dto?.totalRounds ?? 3, roundSeconds: dto?.roundSeconds ?? 10 };
}

/** A live game as the game page shows it (from `GameSnapshotDto`, then kept up to date by events). */
export interface GameView {
  gameId: number;
  players: string[];
  settings: GameSettings;
  finished: boolean;
  round: number;
  roundEndsAt: string;
  scores: PlayerScore[];
  deal: Deal;
}

export function toGameView(dto: GameSnapshotDto): GameView {
  return {
    gameId: dto.gameId ?? 0,
    players: dto.players ?? [],
    settings: toSettings(dto.settings),
    finished: dto.phase === 'FINISHED',
    round: dto.round ?? 1,
    roundEndsAt: dto.roundEndsAt ?? new Date().toISOString(),
    scores: toScores(dto.scores),
    deal: toDeal(dto.deal),
  };
}

/** Final result, same rule as `GameFinished` (D3/D4/D5). */
export interface GameResultView {
  gameId: number;
  draw: boolean;
  winner: string | null;
  loser: string | null;
  leaders: string[];
  topScore: number;
  trailers: string[];
  bottomScore: number;
  scores: PlayerScore[];
}

export function toGameResultView(dto: GameResultDto): GameResultView {
  const winner = dto.winner ?? null;
  return {
    gameId: dto.gameId ?? 0,
    draw: dto.draw ?? winner === null,
    winner,
    loser: dto.loser ?? null,
    leaders: dto.leaders ?? [],
    topScore: dto.topScore ?? 0,
    trailers: dto.trailers ?? [],
    bottomScore: dto.bottomScore ?? 0,
    scores: toScores(dto.scores),
  };
}

export interface InvitationView {
  id: number;
  inviter: string;
  invitees: string[];
  accepted: string[];
  settings: GameSettings;
  status: InvitationStatus;
  declinedBy: string | null;
  expiresAt: string;
}

export function toInvitationView(dto: InvitationDto): InvitationView {
  return {
    id: dto.id ?? 0,
    inviter: dto.inviter ?? '',
    invitees: dto.invitees ?? [],
    accepted: dto.accepted ?? [],
    settings: toSettings(dto.settings),
    status: dto.status ?? 'PENDING',
    declinedBy: dto.declinedBy ?? null,
    expiresAt: dto.expiresAt ?? new Date().toISOString(),
  };
}
