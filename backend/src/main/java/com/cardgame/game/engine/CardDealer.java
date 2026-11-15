package com.cardgame.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Deals one card per player and derives the center card from the dealt cards.
 *
 * <p>Draw order (contract, pinned by the parity tests): for each player in order, {@code nextInt(11)} for
 * the symbol then {@code nextInt(3)} for the color; then {@code nextInt(n)} picks the player whose symbol
 * becomes the center symbol and {@code nextInt(n)} the player whose color becomes the center color.
 */
public final class CardDealer {

    /** The 11 legacy symbols, without {@code G} (D1). */
    public static final List<Character> SYMBOLS =
            List.of('A', 'B', 'C', 'D', 'E', 'F', 'H', 'I', 'J', 'K', 'L');

    /** Card colors in legacy order. */
    public static final List<CardColor> COLORS = List.of(CardColor.RED, CardColor.BLUE, CardColor.GREEN);

    private final RandomGenerator random;

    public CardDealer(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * @throws IllegalArgumentException if {@code players} is empty
     */
    public Deal deal(List<String> players) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Cannot deal to an empty player list");
        }
        List<PlayerCard> cards = new ArrayList<>(players.size());
        for (String player : players) {
            char symbol = SYMBOLS.get(random.nextInt(SYMBOLS.size()));
            CardColor color = COLORS.get(random.nextInt(COLORS.size()));
            cards.add(new PlayerCard(player, new Card(symbol, color)));
        }
        char centerSymbol = cards.get(random.nextInt(cards.size())).card().symbol();
        CardColor centerColor = cards.get(random.nextInt(cards.size())).card().color();
        return new Deal(cards, new Card(centerSymbol, centerColor));
    }
}
