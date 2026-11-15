package com.cardgame.game;

import com.cardgame.common.NotFoundException;

/** 404: unknown game, or not live any more. */
public class GameNotFoundException extends NotFoundException {

    public GameNotFoundException(long gameId) {
        super("Partie introuvable", "Aucune partie en cours avec l'identifiant %d.".formatted(gameId));
    }
}
