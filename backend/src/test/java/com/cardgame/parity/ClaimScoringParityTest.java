package com.cardgame.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.cardgame.game.engine.Card;
import com.cardgame.game.engine.CardColor;
import com.cardgame.game.engine.Claim;
import com.cardgame.game.engine.ClaimOutcome;
import com.cardgame.game.engine.ClaimRules;
import com.cardgame.game.engine.ClaimStatus;
import com.cardgame.game.engine.GameEngine;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.game.engine.GameState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Claim ("combination") and scoring rules.
 *
 * <p>legacy:
 * <ul>
 *   <li>claim checks and +1: legacy/src/main/webApp/game.jsp, handleSameColorButton lines 255-273,
 *       handleSameFormatButton lines 274-293, handleboth lines 295-315</li>
 *   <li>scores broadcast + new cards after every accepted score update:
 *       legacy/src/main/java/webSocket/UserStatusWebSocket.java, handleUpdateScore lines 323-327,
 *       broadcastUpdatedScores lines 330-342</li>
 * </ul>
 * Legacy computes scores in the browser and the server trusts them (lost updates, cheating). The new
 * engine is authoritative. DECISION Q8: the rule itself is unchanged (+1, no penalty, BOTH = 1 point),
 * and only the first correct claim on a given {@code dealVersion} scores.
 */
class ClaimScoringParityTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final GameSettings SETTINGS = new GameSettings(3, Duration.ofSeconds(10));

    // No penalty for a wrong claim, and BOTH earns the same single point as SAME_COLOR / SAME_SYMBOL.
    // A SAME_COLOR claim is also correct when both symbol and color match. (Kept as legacy: Q8.)
    @ParameterizedTest(name = "player {0}/{1} vs center {2}/{3}, {4} -> correct={5}, points={6}")
    @CsvSource({
            // playerSymbol, playerColor, centerSymbol, centerColor, claim, correct, points
            "A, RED,   A, RED,   SAME_COLOR,  true,  1",
            "A, RED,   A, RED,   SAME_SYMBOL, true,  1",
            "A, RED,   A, RED,   BOTH,        true,  1",
            "A, RED,   A, BLUE,  SAME_COLOR,  false, 0",
            "A, RED,   A, BLUE,  SAME_SYMBOL, true,  1",
            "A, RED,   A, BLUE,  BOTH,        false, 0",
            "A, RED,   B, RED,   SAME_COLOR,  true,  1",
            "A, RED,   B, RED,   SAME_SYMBOL, false, 0",
            "A, RED,   B, RED,   BOTH,        false, 0",
            "A, RED,   B, BLUE,  SAME_COLOR,  false, 0",
            "A, RED,   B, BLUE,  SAME_SYMBOL, false, 0",
            "A, RED,   B, BLUE,  BOTH,        false, 0",
            "L, GREEN, L, GREEN, BOTH,        true,  1",
            "H, GREEN, F, GREEN, SAME_COLOR,  true,  1",
    })
    void judgesAClaimAgainstTheCenterCard(char playerSymbol, CardColor playerColor,
                                          char centerSymbol, CardColor centerColor,
                                          Claim claim, boolean correct, int points) {
        Card player = new Card(playerSymbol, playerColor);
        Card center = new Card(centerSymbol, centerColor);

        assertThat(ClaimRules.isCorrect(claim, player, center)).isEqualTo(correct);
        assertThat(ClaimRules.pointsFor(claim, player, center)).isEqualTo(points);
    }

    @Test
    void firstDealOfAGameHasVersionOne() {
        GameEngine engine = new GameEngine(
                ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0), new MutableClock(T0));

        GameState started = engine.start(List.of("alice", "bob"), SETTINGS);

        assertThat(started.dealVersion()).isEqualTo(1);
    }

    @Test
    void correctClaimAddsOnePointToTheClaimerOnlyAndDealsNewCards() {
        // given: alice A/RED, bob L/GREEN, center L/RED (see CardGenerationParityTest)
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0)
                // next deal, drawn after the correct claim: alice B/BLUE, bob C/BLUE, center B/BLUE
                .then(11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1);
        GameEngine engine = new GameEngine(random, new MutableClock(T0));
        GameState started = engine.start(List.of("alice", "bob"), SETTINGS);

        // when: alice's color RED equals center color RED
        ClaimOutcome outcome = engine.claim(started, "alice", Claim.SAME_COLOR, 1);

        // then
        assertThat(outcome.status()).isEqualTo(ClaimStatus.CORRECT);
        assertThat(outcome.correct()).isTrue();
        assertThat(outcome.state().scores()).containsExactly(entry("alice", 1), entry("bob", 0));
        assertThat(outcome.state().deal().cardOf("alice")).isEqualTo(new Card('B', CardColor.BLUE));
        assertThat(outcome.state().deal().centerCard()).isEqualTo(new Card('B', CardColor.BLUE));
        assertThat(outcome.state().dealVersion()).isEqualTo(2);
        assertThat(outcome.state().currentRound()).isEqualTo(1);
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    void bothClaimEarnsASinglePoint() {
        // given: alice A/RED, bob A/BLUE, center A (from alice) / RED (from alice)
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 0, 3, 1, 2, 0, 2, 0)
                .then(11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1);
        GameEngine engine = new GameEngine(random, new MutableClock(T0));
        GameState started = engine.start(List.of("alice", "bob"), SETTINGS);

        // when
        ClaimOutcome outcome = engine.claim(started, "alice", Claim.BOTH, started.dealVersion());

        // then
        assertThat(outcome.correct()).isTrue();
        assertThat(outcome.state().scores().get("alice")).isEqualTo(1);
    }

    @Test
    void wrongClaimChangesNothingAndKeepsTheSameCards() {
        // given: alice A/RED, bob L/GREEN, center L/RED
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0);
        GameEngine engine = new GameEngine(random, new MutableClock(T0));
        GameState started = engine.start(List.of("alice", "bob"), SETTINGS);

        // when: alice's symbol A differs from center symbol L
        ClaimOutcome outcome = engine.claim(started, "alice", Claim.SAME_SYMBOL, started.dealVersion());

        // then: no penalty, no new deal (no random draw), same deal version
        assertThat(outcome.status()).isEqualTo(ClaimStatus.WRONG);
        assertThat(outcome.correct()).isFalse();
        assertThat(outcome.state()).isEqualTo(started);
        assertThat(random.isExhausted()).isTrue();
    }

    // DECISION Q8: legacy let every browser score against whatever cards it currently displayed, so two
    // players could both score on the same deal (and overwrite each other's maps). Now only the first
    // correct claim on a deal scores; a claim that names an outdated dealVersion is STALE.
    @Test
    void secondCorrectClaimOnTheSameDealIsStale() {
        // given: alice A/RED, bob A/BLUE, center A/RED -> both could claim SAME_SYMBOL on deal 1
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 0, 3, 1, 2, 0, 2, 0)
                .then(11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1);
        GameEngine engine = new GameEngine(random, new MutableClock(T0));
        GameState started = engine.start(List.of("alice", "bob"), SETTINGS);
        GameState afterAlice = engine.claim(started, "alice", Claim.SAME_SYMBOL, 1).state();

        // when: bob's claim still refers to deal 1
        ClaimOutcome bob = engine.claim(afterAlice, "bob", Claim.SAME_SYMBOL, 1);

        // then
        assertThat(bob.status()).isEqualTo(ClaimStatus.STALE);
        assertThat(bob.correct()).isFalse();
        assertThat(bob.state()).isEqualTo(afterAlice);
        assertThat(bob.state().scores()).containsExactly(entry("alice", 1), entry("bob", 0));
        assertThat(random.isExhausted()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"0", "2", "99", "-1"})
    void claimWithAnotherDealVersionIsStaleEvenIfItWouldBeCorrect(int dealVersion) {
        // given: alice A/RED, center L/RED -> SAME_COLOR would be correct on deal 1
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0);
        GameEngine engine = new GameEngine(random, new MutableClock(T0));
        GameState started = engine.start(List.of("alice", "bob"), SETTINGS);

        ClaimOutcome outcome = engine.claim(started, "alice", Claim.SAME_COLOR, dealVersion);

        assertThat(outcome.status()).isEqualTo(ClaimStatus.STALE);
        assertThat(outcome.state()).isEqualTo(started);
    }

    @Test
    void rejectsAClaimFromSomeoneWhoIsNotInTheGame() {
        GameEngine engine = new GameEngine(
                ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0), new MutableClock(T0));
        GameState started = engine.start(List.of("alice", "bob"), SETTINGS);

        assertThatThrownBy(() -> engine.claim(started, "mallory", Claim.BOTH, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
