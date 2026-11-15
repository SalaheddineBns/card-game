package com.cardgame.game;

import com.cardgame.common.DomainException;
import org.springframework.http.HttpStatus;

/** 403: the user is not a player of this game. */
public class NotAGamePlayerException extends DomainException {

    public NotAGamePlayerException(long gameId) {
        super(HttpStatus.FORBIDDEN, "Accès refusé", "Vous ne participez pas à la partie %d.".formatted(gameId));
    }
}
