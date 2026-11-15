package com.cardgame.invitation;

import com.cardgame.game.engine.GameSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

/** Mutable in-memory invitation; guarded by the {@link InvitationService} lock. */
final class Invitation {

    final long id;
    final String inviter;
    final List<String> invitees;
    final GameSettings settings;
    final Instant expiresAt;
    final List<String> accepted = new ArrayList<>();
    InvitationStatus status = InvitationStatus.PENDING;
    String declinedBy;
    Long gameId;
    ScheduledFuture<?> expiry;

    Invitation(long id, String inviter, List<String> invitees, GameSettings settings, Instant expiresAt) {
        this.id = id;
        this.inviter = inviter;
        this.invitees = List.copyOf(invitees);
        this.settings = settings;
        this.expiresAt = expiresAt;
    }

    /** Canonical invitee name, matched ignoring case. */
    Optional<String> invitee(String username) {
        return invitees.stream().filter(i -> same(i, username)).findFirst();
    }

    boolean involves(String username) {
        return same(inviter, username) || invitee(username).isPresent();
    }

    List<String> participants() {
        List<String> all = new ArrayList<>(invitees.size() + 1);
        all.add(inviter);
        all.addAll(invitees);
        return all;
    }

    static boolean same(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}
