package com.cardgame.game;

/** Persistent status of a game row (D22, D25). */
public enum GameStatus {
    IN_PROGRESS,
    FINISHED,
    /** Interrupted (server restart, or the end of the game could not be saved): no participations, no stats. */
    ABANDONED
}
