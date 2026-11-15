package com.cardgame.game.engine;

public enum ClaimStatus {
    /** +1 for the claimer and a new deal. */
    CORRECT,
    /** The claim does not hold for the current deal; nothing happens. */
    WRONG,
    /** The claim named another deal version than the current one; nothing happens. */
    STALE
}
