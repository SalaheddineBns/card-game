package com.cardgame.game.dto;

import java.time.Instant;
import java.util.List;

/** Result of a finished game; {@code winner}/{@code loser} are null when there is no single one. */
public record GameResultDto(long gameId, Instant finishedAt, boolean draw,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true, description = "null when there is no single winner (draw)") String winner,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true, description = "null when there is no single loser") String loser, List<String> leaders, int topScore, List<String> trailers, int bottomScore, List<PlayerScoreDto> scores) {
}
