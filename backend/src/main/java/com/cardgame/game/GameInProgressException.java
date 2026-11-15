package com.cardgame.game;

import com.cardgame.common.ConflictException;

/** 409: the result is not available yet. */
public class GameInProgressException extends ConflictException {

    public GameInProgressException(long gameId) {
        super("Partie en cours", "La partie %d n'est pas encore terminée.".formatted(gameId));
    }
}
