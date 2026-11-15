package com.cardgame.invitation;

import com.cardgame.common.ConflictException;

/** 409: a participant is offline, in a game or already in a pending invitation. */
public class PlayerUnavailableException extends ConflictException {

    public PlayerUnavailableException(String detail) {
        super("Joueur indisponible", detail);
    }
}
