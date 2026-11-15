package com.cardgame.game.engine;

import java.util.List;

/**
 * Final result of a game (D3, D4, D5).
 *
 * @param winner the single leader, {@code null} when several players share the top score (draw)
 * @param loser the single trailer, {@code null} when there is none
 * @param leaders players with the top score, in player order
 * @param trailers players with the bottom score, in player order; empty when every score is equal
 * @param outcomes one entry per player, in player order
 */
public record GameResult(String winner, String loser, List<String> leaders, int topScore,
                         List<String> trailers, int bottomScore, List<PlayerOutcome> outcomes) {

    public GameResult {
        leaders = List.copyOf(leaders);
        trailers = List.copyOf(trailers);
        outcomes = List.copyOf(outcomes);
    }

    public boolean isDraw() {
        return winner == null;
    }
}
