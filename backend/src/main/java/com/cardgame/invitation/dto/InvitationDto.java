package com.cardgame.invitation.dto;

import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.invitation.InvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record InvitationDto(
        long id, String inviter, List<String> invitees, List<String> accepted, GameSettingsDto settings,
        InvitationStatus status,
        @Schema(nullable = true) String declinedBy,
        Instant expiresAt,
        @Schema(nullable = true, description = "Set once ACCEPTED") Long gameId) {
}
