package com.cardgame.game.engine;

/** Judges a claim against the center card (D8: +1 for any correct claim, no penalty). */
public final class ClaimRules {

    private ClaimRules() {
    }

    public static boolean isCorrect(Claim claim, Card playerCard, Card centerCard) {
        boolean sameColor = playerCard.color() == centerCard.color();
        boolean sameSymbol = playerCard.symbol() == centerCard.symbol();
        return switch (claim) {
            case SAME_COLOR -> sameColor;
            case SAME_SYMBOL -> sameSymbol;
            case BOTH -> sameColor && sameSymbol;
        };
    }

    /** @return 1 for a correct claim (BOTH included), 0 otherwise */
    public static int pointsFor(Claim claim, Card playerCard, Card centerCard) {
        return isCorrect(claim, playerCard, centerCard) ? 1 : 0;
    }
}
