package com.cardgame.game;

import com.cardgame.common.ConflictException;

/** 409: the game is over (use the result endpoint). */
public class GameFinishedException extends ConflictException {

    public GameFinishedException(long gameId) {
        super("Partie terminée", "La partie %d est terminée. Consultez son résultat.".formatted(gameId));
    }
}
