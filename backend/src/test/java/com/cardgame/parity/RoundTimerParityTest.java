package com.cardgame.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.cardgame.game.engine.Claim;
import com.cardgame.game.engine.GameEngine;
import com.cardgame.game.engine.GamePhase;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.game.engine.GameState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Game start, rounds and round timer.
 *
 * <p>legacy:
 * <ul>
 *   <li>game start, initial scores all 0: UserStatusWebSocket#beginGame lines 379-432 (scores 414-417)</li>
 *   <li>round counter starts at 1: game.jsp initializeGame lines 107-109</li>
 *   <li>timer per round, next round or end of game: game.jsp startTimer lines 189-207</li>
 *   <li>round advance broadcast {@code tour + 1}: UserStatusWebSocket#handleFinTour lines 264-276</li>
 *   <li>new cards at round change: case "finTour" lines 77-81 calls handleUpdateScore before handleFinTour</li>
 * </ul>
 * Legacy runs one timer per browser; the new server runs one timer per game and calls
 * {@code GameEngine#roundTimerExpired} when it fires. Round semantics are the same: N rounds of T seconds.
 */
@Disabled("pending port: rounds/cards/timer (plan rows 10-11) - implement com.cardgame.game.engine.GameEngine")
class RoundTimerParityTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void startsAtRoundOneWithZeroScoresInPlayerOrderAndARoundDeadline() {
        // given
        MutableClock clock = new MutableClock(T0);
        GameEngine engine = new GameEngine(new Random(7), clock);

        // when
        GameState state = engine.start(List.of("carol", "alice", "bob"), new GameSettings(3, Duration.ofSeconds(10), 0));

        // then
        assertThat(state.phase()).isEqualTo(GamePhase.IN_PROGRESS);
        assertThat(state.currentRound()).isEqualTo(1);
        assertThat(state.players()).containsExactly("carol", "alice", "bob");
        assertThat(state.scores()).containsExactly(entry("carol", 0), entry("alice", 0), entry("bob", 0));
        assertThat(state.deal().playerCards()).hasSize(3);
        assertThat(state.roundStartedAt()).isEqualTo(T0);
        assertThat(state.roundEndsAt()).isEqualTo(T0.plusSeconds(10));
    }

    @ParameterizedTest(name = "{0} rounds, timer expired {1}x -> round {2}, {3}")
    @CsvSource({
            // totalRounds, expiries, expectedRound, expectedPhase
            "3, 0, 1, IN_PROGRESS",
            "3, 1, 2, IN_PROGRESS",
            "3, 2, 3, IN_PROGRESS",
            "3, 3, 3, FINISHED",
            "1, 1, 1, FINISHED",
            "5, 4, 5, IN_PROGRESS",
            "5, 5, 5, FINISHED",
    })
    void advancesOneRoundPerTimerExpiryAndFinishesAfterTheLastRound(
            int totalRounds, int expiries, int expectedRound, GamePhase expectedPhase) {
        // given
        MutableClock clock = new MutableClock(T0);
        GameEngine engine = new GameEngine(new Random(7), clock);
        GameState state = engine.start(List.of("alice", "bob"), new GameSettings(totalRounds, Duration.ofSeconds(10), 0));

        // when
        for (int i = 0; i < expiries; i++) {
            clock.advance(Duration.ofSeconds(10));
            state = engine.roundTimerExpired(state);
        }

        // then
        assertThat(state.currentRound()).isEqualTo(expectedRound);
        assertThat(state.phase()).isEqualTo(expectedPhase);
    }

    @Test
    void newRoundRestartsTheDeadlineFromNowAndDealsNewCards() {
        // given: alice A/RED, bob L/GREEN, center L/RED; then at round 2: alice B/BLUE, bob C/BLUE, center B/BLUE
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0)
                .then(11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1);
        MutableClock clock = new MutableClock(T0);
        GameEngine engine = new GameEngine(random, clock);
        GameState round1 = engine.start(List.of("alice", "bob"), new GameSettings(2, Duration.ofSeconds(15), 0));

        // when
        clock.advance(Duration.ofSeconds(15));
        GameState round2 = engine.roundTimerExpired(round1);

        // then
        assertThat(round2.currentRound()).isEqualTo(2);
        assertThat(round2.roundStartedAt()).isEqualTo(T0.plusSeconds(15));
        assertThat(round2.roundEndsAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(round2.deal()).isNotEqualTo(round1.deal());
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    void scoresCarryOverFromOneRoundToTheNext() {
        // given: alice A/RED, bob L/GREEN, center L/RED; alice claims SAME_COLOR correctly
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0)
                .then(11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1)   // deal after the claim
                .then(11, 3, 3, 0, 11, 4, 3, 2, 2, 0, 2, 0);  // deal for round 2
        MutableClock clock = new MutableClock(T0);
        GameEngine engine = new GameEngine(random, clock);
        GameState state = engine.start(List.of("alice", "bob"), new GameSettings(2, Duration.ofSeconds(10), 0));
        state = engine.claim(state, "alice", Claim.SAME_COLOR).state();

        // when
        clock.advance(Duration.ofSeconds(10));
        state = engine.roundTimerExpired(state);

        // then
        assertThat(state.scores()).containsExactly(entry("alice", 1), entry("bob", 0));
    }

    @Test
    void finishingKeepsTheFinalScoresAndDealsNoNewCards() {
        // given: one round only
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0);
        MutableClock clock = new MutableClock(T0);
        GameEngine engine = new GameEngine(random, clock);
        GameState state = engine.start(List.of("alice", "bob"), new GameSettings(1, Duration.ofSeconds(10), 0));

        // when
        clock.advance(Duration.ofSeconds(10));
        GameState finished = engine.roundTimerExpired(state);

        // then
        assertThat(finished.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(finished.scores()).isEqualTo(state.scores());
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    void rejectsTimerExpiryAndClaimsOnceTheGameIsFinished() {
        MutableClock clock = new MutableClock(T0);
        GameEngine engine = new GameEngine(new Random(7), clock);
        GameState state = engine.start(List.of("alice", "bob"), new GameSettings(1, Duration.ofSeconds(10), 0));
        GameState finished = engine.roundTimerExpired(state);

        assertThatThrownBy(() -> engine.roundTimerExpired(finished)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> engine.claim(finished, "alice", Claim.BOTH)).isInstanceOf(IllegalStateException.class);
    }
}
