package com.cardgame.invitation.dto;

import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;
import java.util.List;

public record InvitationReceived(long invitationId, String inviter, List<String> invitees,
                                 GameSettingsDto settings, Instant expiresAt, Instant sentAt) implements RealtimeEvent {
}
