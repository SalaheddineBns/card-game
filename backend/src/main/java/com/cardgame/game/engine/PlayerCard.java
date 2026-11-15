package com.cardgame.game.engine;

import java.util.Objects;

/** The card a given player currently holds. */
public record PlayerCard(String player, Card card) {

    public PlayerCard {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(card, "card");
    }
}
