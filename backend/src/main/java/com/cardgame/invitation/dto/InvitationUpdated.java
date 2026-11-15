package com.cardgame.invitation.dto;

import com.cardgame.invitation.InvitationStatus;
import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;
import java.util.List;

public record InvitationUpdated(long invitationId, InvitationStatus status, List<String> accepted,
                                String declinedBy, Instant sentAt) implements RealtimeEvent {
}
