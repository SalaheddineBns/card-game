package com.cardgame.game.engine;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * Immutable snapshot of a running or finished game. Safe to share across threads; every transition in
 * {@link GameEngine} returns a new instance.
 *
 * @param scores player → score, in player order
 * @param dealVersion starts at 1, +1 for every new deal (D8)
 */
public record GameState(List<String> players, GameSettings settings, GamePhase phase, int currentRound,
                        SequencedMap<String, Integer> scores, Deal deal, int dealVersion,
                        Instant roundStartedAt) {

    public GameState {
        players = List.copyOf(players);
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(phase, "phase");
        scores = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(scores));
        Objects.requireNonNull(deal, "deal");
        Objects.requireNonNull(roundStartedAt, "roundStartedAt");
    }

    /** {@code roundStartedAt + settings.roundDuration}. */
    public Instant roundEndsAt() {
        return roundStartedAt.plus(settings.roundDuration());
    }
}
