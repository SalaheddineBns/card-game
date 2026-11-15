package com.cardgame.invitation;

import com.cardgame.common.NotFoundException;

/** 404: unknown invitation, or the user is not one of its invitees. */
public class InvitationNotFoundException extends NotFoundException {

    public InvitationNotFoundException(long invitationId) {
        super("Invitation introuvable", "Aucune invitation %d ne vous est adressée.".formatted(invitationId));
    }
}
