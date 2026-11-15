package com.cardgame.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardgame.game.engine.Card;
import com.cardgame.game.engine.CardColor;
import com.cardgame.game.engine.CardDealer;
import com.cardgame.game.engine.Deal;
import com.cardgame.game.engine.PlayerCard;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Card generation ("deal") rules.
 *
 * <p>legacy: legacy/src/main/java/webSocket/UserStatusWebSocket.java
 * <ul>
 *   <li>symbols and colors: lines 27-28</li>
 *   <li>deal at game start: beginGame, lines 388-402</li>
 *   <li>deal after every score update / round change: generateGameCards, lines 452-466 (called from
 *       broadcastUpdatedScores, lines 330-342)</li>
 * </ul>
 * The unused {@code generateCards} (lines 346-376, 13 unique letters A-M) is dead code and is NOT the reference.
 *
 * <p>Draw order (contract): for each player in the given order, {@code nextInt(11)} for the symbol then
 * {@code nextInt(3)} for the color; then {@code nextInt(playerCount)} picks the player whose symbol becomes
 * the center symbol, then {@code nextInt(playerCount)} picks the player whose color becomes the center color.
 */
@Disabled("pending port: rounds/cards/timer (plan row 10-11) - implement com.cardgame.game.engine.CardDealer")
class CardGenerationParityTest {

    // DECISION Q1: keep the legacy symbol list, which skips 'G' (A-F, H-L = 11 letters).
    // (The unused legacy generateCards() uses A-M; it is not the reference.)
    @Test
    void symbolsAreTheElevenLegacyLettersWithoutG() {
        assertThat(CardDealer.SYMBOLS)
                .containsExactly('A', 'B', 'C', 'D', 'E', 'F', 'H', 'I', 'J', 'K', 'L');
    }

    @Test
    void colorsAreRedBlueGreenInLegacyOrder() {
        assertThat(CardDealer.COLORS).containsExactly(CardColor.RED, CardColor.BLUE, CardColor.GREEN);
    }

    @Test
    void colorWireValuesAreLowerCaseLikeLegacy() {
        // legacy sends "red" / "blue" / "green" and the JSP uses them directly as CSS colors
        assertThat(CardColor.RED.wireValue()).isEqualTo("red");
        assertThat(CardColor.BLUE.wireValue()).isEqualTo("blue");
        assertThat(CardColor.GREEN.wireValue()).isEqualTo("green");
    }

    static Stream<Arguments> scriptedDeals() {
        return Stream.of(
                // Two players. Center symbol comes from bob (index 1), center color from alice (index 0).
                // Note: the center card (L, RED) is held by nobody, so a BOTH claim is impossible this deal.
                // This is legacy behaviour (symbol and color are picked independently), not flagged as a bug.
                Arguments.of(
                        List.of("alice", "bob"),
                        new int[] {11, 0, 3, 0, /* bob */ 11, 10, 3, 2, /* center */ 2, 1, 2, 0},
                        List.of(new PlayerCard("alice", new Card('A', CardColor.RED)),
                                new PlayerCard("bob", new Card('L', CardColor.GREEN))),
                        new Card('L', CardColor.RED)),
                // Three players. Duplicate cards between players are allowed (p1 and p2 both get H/BLUE).
                Arguments.of(
                        List.of("p1", "p2", "p3"),
                        new int[] {11, 6, 3, 1, /* p2 */ 11, 6, 3, 1, /* p3 */ 11, 3, 3, 2, /* center */ 3, 2, 3, 0},
                        List.of(new PlayerCard("p1", new Card('H', CardColor.BLUE)),
                                new PlayerCard("p2", new Card('H', CardColor.BLUE)),
                                new PlayerCard("p3", new Card('D', CardColor.GREEN))),
                        new Card('D', CardColor.BLUE)),
                // Index 5 is 'F' and index 6 is 'H' (no 'G').
                Arguments.of(
                        List.of("x", "y"),
                        new int[] {11, 5, 3, 2, /* y */ 11, 6, 3, 1, /* center */ 2, 0, 2, 1},
                        List.of(new PlayerCard("x", new Card('F', CardColor.GREEN)),
                                new PlayerCard("y", new Card('H', CardColor.BLUE))),
                        new Card('F', CardColor.BLUE)));
    }

    @ParameterizedTest
    @MethodSource("scriptedDeals")
    void dealsOneCardPerPlayerAndDerivesTheCenterCardFromPlayerCards(
            List<String> players, int[] script, List<PlayerCard> expectedPlayerCards, Card expectedCenter) {
        // given
        ScriptedRandom random = ScriptedRandom.of(script);
        CardDealer dealer = new CardDealer(random);

        // when
        Deal deal = dealer.deal(players);

        // then
        assertThat(deal.playerCards()).containsExactlyElementsOf(expectedPlayerCards);
        assertThat(deal.centerCard()).isEqualTo(expectedCenter);
        assertThat(random.isExhausted()).as("engine consumed exactly the scripted draws").isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 7L, 42L, 1234L, 987_654_321L})
    void centerSymbolAndColorAlwaysComeFromSomePlayersCard(long seed) {
        // given
        List<String> players = List.of("alice", "bob", "carol", "dave");
        CardDealer dealer = new CardDealer(new Random(seed));

        for (int i = 0; i < 200; i++) {
            // when
            Deal deal = dealer.deal(players);

            // then
            assertThat(deal.playerCards()).extracting(PlayerCard::player).containsExactlyElementsOf(players);
            assertThat(deal.playerCards()).allSatisfy(pc -> {
                assertThat(CardDealer.SYMBOLS).contains(pc.card().symbol());
                assertThat(CardDealer.COLORS).contains(pc.card().color());
            });
            assertThat(deal.playerCards()).extracting(pc -> pc.card().symbol())
                    .contains(deal.centerCard().symbol());
            assertThat(deal.playerCards()).extracting(pc -> pc.card().color())
                    .contains(deal.centerCard().color());
        }
    }

    @Test
    void cardOfReturnsThePlayersCurrentCard() {
        Deal deal = new CardDealer(ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0))
                .deal(List.of("alice", "bob"));

        assertThat(deal.cardOf("bob")).isEqualTo(new Card('L', CardColor.GREEN));
    }

    @Test
    void rejectsAnEmptyPlayerList() {
        // legacy: Random.nextInt(0) throws IllegalArgumentException (line 400 / 461)
        CardDealer dealer = new CardDealer(new Random(1));

        assertThatThrownBy(() -> dealer.deal(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
