import { GameResultDto, GameSnapshotDto, InvitationDto } from '../../core/api';
import { Deal, GameSettings, InvitationStatus, PlayerScore } from '../../core/realtime/protocol';

/*
 * REST DTOs → the same view types the STOMP events use (core/realtime/protocol.ts), so each
 * feature handles one model. The generated DTOs are strict and structurally identical to the
 * protocol types (string enums vs. literal unions), so these are mostly field selections.
 */

/** A live game as the game page shows it (from `GameSnapshotDto`, then kept up to date by events). */
export interface GameView {
  gameId: number;
  players: string[];
  settings: GameSettings;
  finished: boolean;
  round: number;
  roundStartedAt: string;
  roundEndsAt: string;
  scores: PlayerScore[];
  deal: Deal;
}

export function toGameView(dto: GameSnapshotDto): GameView {
  return {
    gameId: dto.gameId,
    players: dto.players,
    settings: dto.settings,
    finished: dto.phase === 'FINISHED',
    round: dto.round,
    roundStartedAt: dto.roundStartedAt,
    roundEndsAt: dto.roundEndsAt,
    scores: dto.scores,
    deal: dto.deal,
  };
}

/** Final result, same rule as `GameFinished` (D3/D4/D5). */
export type GameResultView = Omit<GameResultDto, 'finishedAt'>;

export function toGameResultView(dto: GameResultDto): GameResultView {
  return dto;
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
    id: dto.id,
    inviter: dto.inviter,
    invitees: dto.invitees,
    accepted: dto.accepted,
    settings: dto.settings,
    status: dto.status,
    declinedBy: dto.declinedBy,
    expiresAt: dto.expiresAt,
  };
}
