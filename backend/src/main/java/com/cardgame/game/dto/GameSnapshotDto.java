package com.cardgame.game.dto;

import com.cardgame.game.engine.GamePhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Live view of a game for one of its players.
 *
 * @param roundStartedAt start of the current round ({@code roundEndsAt - settings.roundSeconds})
 * @param serverTime server clock when the snapshot was taken, so the client can correct its own clock
 *     ({@code offset = serverTime - Date.now()} at receipt). Response metadata: ignored by {@code equals}.
 */
public record GameSnapshotDto(long gameId, List<String> players, GameSettingsDto settings, GamePhase phase,
                              int round, Instant roundStartedAt, Instant roundEndsAt, List<PlayerScoreDto> scores,
                              DealDto deal, @Schema(description = "Server clock when the snapshot was taken") Instant serverTime) {

    /** The contract's shape (no server time); {@code roundStartedAt} is derived from the round length. */
    public GameSnapshotDto(long gameId, List<String> players, GameSettingsDto settings, GamePhase phase, int round,
                           Instant roundEndsAt, List<PlayerScoreDto> scores, DealDto deal) {
        this(gameId, players, settings, phase, round, roundEndsAt.minusSeconds(settings.roundSeconds()), roundEndsAt,
                scores, deal, null);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GameSnapshotDto other
                && gameId == other.gameId && round == other.round
                && Objects.equals(players, other.players) && Objects.equals(settings, other.settings)
                && phase == other.phase && Objects.equals(roundStartedAt, other.roundStartedAt)
                && Objects.equals(roundEndsAt, other.roundEndsAt) && Objects.equals(scores, other.scores)
                && Objects.equals(deal, other.deal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameId, players, settings, phase, round, roundStartedAt, roundEndsAt, scores, deal);
    }
}
