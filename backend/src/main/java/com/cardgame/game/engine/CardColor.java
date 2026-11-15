package com.cardgame.game.engine;

import java.util.Locale;

/** Color of a runtime card (legacy {@code color}). Sent on the wire in lower case, like legacy. */
public enum CardColor {
    RED,
    BLUE,
    GREEN;

    /** Lower-case value used in JSON payloads: {@code "red"}, {@code "blue"}, {@code "green"}. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
