package com.cardgame.game.engine;

import java.util.List;
import java.util.Objects;

/** One distribution of player cards (in player order) and the shared center card. */
public record Deal(List<PlayerCard> playerCards, Card centerCard) {

    public Deal {
        playerCards = List.copyOf(playerCards);
        Objects.requireNonNull(centerCard, "centerCard");
    }

    /**
     * @return the card currently held by {@code player}
     * @throws IllegalArgumentException if the player is not part of this deal
     */
    public Card cardOf(String player) {
        return playerCards.stream()
                .filter(pc -> pc.player().equals(player))
                .map(PlayerCard::card)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player in deal: " + player));
    }
}
