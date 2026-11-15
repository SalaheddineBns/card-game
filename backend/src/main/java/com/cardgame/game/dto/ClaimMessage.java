package com.cardgame.game.dto;

import com.cardgame.game.engine.Claim;
import jakarta.validation.constraints.NotNull;

/** STOMP SEND payload of {@code /app/game/{gameId}/claim}. */
public record ClaimMessage(@NotNull(message = "La revendication est obligatoire.") Claim claim, int dealVersion) {
}
