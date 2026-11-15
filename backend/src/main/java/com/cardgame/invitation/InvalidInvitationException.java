package com.cardgame.invitation;

import com.cardgame.common.DomainException;
import org.springframework.http.HttpStatus;

/** 400: empty invitee list, duplicates, self-invitation or invalid settings. */
public class InvalidInvitationException extends DomainException {

    public InvalidInvitationException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invitation invalide", detail);
    }
}
