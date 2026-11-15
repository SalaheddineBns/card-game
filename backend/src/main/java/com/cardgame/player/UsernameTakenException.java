package com.cardgame.player;

import com.cardgame.common.ConflictException;

public class UsernameTakenException extends ConflictException {

    public UsernameTakenException(String username) {
        super("Nom d'utilisateur déjà pris", "Le nom d'utilisateur « %s » est déjà utilisé.".formatted(username));
    }
}
