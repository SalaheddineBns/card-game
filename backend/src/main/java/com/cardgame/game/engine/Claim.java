package com.cardgame.game.engine;

/** What a player claims about their card versus the center card. */
public enum Claim {
    /** "The Same Color": my color equals the center color. */
    SAME_COLOR,
    /** "The Same Format": my symbol equals the center symbol. */
    SAME_SYMBOL,
    /** "The Two": both symbol and color match. */
    BOTH
}
