package com.cardgame.game;

import com.cardgame.common.DomainException;
import org.springframework.http.HttpStatus;

/** 410 (D25): the game was interrupted and will never have a result. */
public class GameAbandonedException extends DomainException {

    public GameAbandonedException() {
        super(HttpStatus.GONE, "Partie interrompue", "Cette partie a été interrompue par un redémarrage du serveur.");
    }
}
