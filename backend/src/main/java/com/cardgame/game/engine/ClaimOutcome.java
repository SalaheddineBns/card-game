package com.cardgame.game.engine;

import java.util.Objects;

/** Result of {@link GameEngine#claim}: the status and the (possibly unchanged) state. */
public record ClaimOutcome(ClaimStatus status, GameState state) {

    public ClaimOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(state, "state");
    }

    public boolean correct() {
        return status == ClaimStatus.CORRECT;
    }
}
