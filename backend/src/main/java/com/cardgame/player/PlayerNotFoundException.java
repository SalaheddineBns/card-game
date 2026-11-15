package com.cardgame.player;

import com.cardgame.common.NotFoundException;

public class PlayerNotFoundException extends NotFoundException {

    public PlayerNotFoundException(String username) {
        super("Joueur introuvable", "Aucun joueur nommé « %s ».".formatted(username));
    }
}
