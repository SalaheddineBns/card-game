package com.cardgame.invitation;

import com.cardgame.common.ConflictException;

/** 409: the invitation is no longer pending. */
public class InvitationClosedException extends ConflictException {

    public InvitationClosedException(long invitationId, InvitationStatus status) {
        super("Invitation fermée", "L'invitation %d n'est plus en attente (%s).".formatted(invitationId, label(status)));
    }

    private static String label(InvitationStatus status) {
        return switch (status) {
            case PENDING -> "en attente";
            case ACCEPTED -> "acceptée";
            case DECLINED -> "refusée";
            case EXPIRED -> "expirée";
        };
    }
}
