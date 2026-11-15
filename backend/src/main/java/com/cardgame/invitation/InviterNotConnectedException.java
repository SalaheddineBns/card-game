package com.cardgame.invitation;

import com.cardgame.common.ConflictException;

/** 409 (D26): the inviter has no open STOMP connection, so they could not follow the invitation. */
public class InviterNotConnectedException extends ConflictException {

    public InviterNotConnectedException() {
        super("Connexion au lobby requise", "Vous devez être connecté au lobby pour inviter des joueurs.");
    }
}
