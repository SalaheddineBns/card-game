package com.cardgame.game.engine;

import java.util.Objects;

/** A runtime card: a symbol (one of {@link CardDealer#SYMBOLS}) and a color. */
public record Card(char symbol, CardColor color) {

    public Card {
        Objects.requireNonNull(color, "color");
    }
}
