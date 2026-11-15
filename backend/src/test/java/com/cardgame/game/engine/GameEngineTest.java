package com.cardgame.game.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Engine behaviour not covered by the parity tests (immutability, guards). */
class GameEngineTest {

    private final GameEngine engine =
            new GameEngine(new Random(1), Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void stateIsIsolatedFromTheCallersMutableList() {
        List<String> players = new ArrayList<>(List.of("alice", "bob"));

        GameState state = engine.start(players, GameSettings.defaults());
        players.add("mallory");

        assertThat(state.players()).containsExactly("alice", "bob");
        assertThat(state.deal().playerCards()).hasSize(2);
    }

    @Test
    void stateCollectionsAreUnmodifiable() {
        GameState state = engine.start(List.of("alice", "bob"), GameSettings.defaults());

        assertThatThrownBy(() -> state.scores().put("alice", 99)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.players().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.deal().playerCards().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultIsRefusedWhileTheGameIsInProgress() {
        GameState state = engine.start(List.of("alice", "bob"), GameSettings.defaults());

        assertThatThrownBy(() -> engine.result(state)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void determineResultRejectsAnEmptyScoreMap() {
        assertThatThrownBy(() -> GameEngine.determineResult(new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cardOfRejectsAPlayerOutsideTheDeal() {
        GameState state = engine.start(List.of("alice", "bob"), GameSettings.defaults());

        assertThatThrownBy(() -> state.deal().cardOf("mallory")).isInstanceOf(IllegalArgumentException.class);
    }
}
