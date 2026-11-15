package com.cardgame.game.dto;

import java.time.Instant;
import java.util.List;

/** Result of a finished game; {@code winner}/{@code loser} are null when there is no single one. */
public record GameResultDto(long gameId, Instant finishedAt, boolean draw, String winner, String loser, List<String> leaders, int topScore, List<String> trailers, int bottomScore, List<PlayerScoreDto> scores) {
}
