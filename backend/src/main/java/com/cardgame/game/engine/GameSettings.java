package com.cardgame.game.engine;

import java.time.Duration;
import java.util.Objects;

/** Settings of one game (D10 limits, no combinations - D9). */
public record GameSettings(int totalRounds, Duration roundDuration) {

    public static final int MIN_ROUNDS = 1;
    public static final int MAX_ROUNDS = 20;
    public static final Duration MIN_ROUND_DURATION = Duration.ofSeconds(5);
    public static final Duration MAX_ROUND_DURATION = Duration.ofSeconds(120);

    public GameSettings {
        Objects.requireNonNull(roundDuration, "roundDuration");
        if (totalRounds < MIN_ROUNDS || totalRounds > MAX_ROUNDS) {
            throw new IllegalArgumentException(
                    "totalRounds must be between %d and %d".formatted(MIN_ROUNDS, MAX_ROUNDS));
        }
        if (roundDuration.compareTo(MIN_ROUND_DURATION) < 0 || roundDuration.compareTo(MAX_ROUND_DURATION) > 0) {
            throw new IllegalArgumentException("roundDuration must be between %d and %d seconds"
                    .formatted(MIN_ROUND_DURATION.toSeconds(), MAX_ROUND_DURATION.toSeconds()));
        }
    }

    /** 3 rounds of 10 seconds. */
    public static GameSettings defaults() {
        return new GameSettings(3, Duration.ofSeconds(10));
    }
}
