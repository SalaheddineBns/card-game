package com.cardgame.game.engine;

/** One player's final score and whether it counts as a victory (only the single winner - D5). */
public record PlayerOutcome(String player, int score, boolean victory) {
}
