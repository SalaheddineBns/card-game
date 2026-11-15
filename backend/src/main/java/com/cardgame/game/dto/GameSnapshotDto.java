package com.cardgame.game.dto;

import com.cardgame.game.engine.GamePhase;
import java.time.Instant;
import java.util.List;

/** Live view of a game for one of its players. */
public record GameSnapshotDto(long gameId, List<String> players, GameSettingsDto settings, GamePhase phase, int round, Instant roundEndsAt, List<PlayerScoreDto> scores, DealDto deal) {
}
