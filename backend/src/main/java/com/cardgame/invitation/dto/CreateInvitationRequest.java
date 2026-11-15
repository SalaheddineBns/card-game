package com.cardgame.invitation.dto;

import com.cardgame.game.dto.GameSettingsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateInvitationRequest(
        @Schema(example = "[\"bob\", \"carol\"]", description = "Online players to invite (1+, not yourself, no duplicates)")
        @NotEmpty(message = "Invitez au moins un joueur.")
        List<@NotBlank(message = "Le nom d'un invité est vide.") String> invitees,
        @Schema(nullable = true, description = "Null → 3 rounds of 10 s")
        @Valid GameSettingsDto settings) {
}
