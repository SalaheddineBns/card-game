package com.cardgame.invitation;

import com.cardgame.game.GameLauncher;
import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.invitation.dto.InvitationDto;
import com.cardgame.invitation.dto.InvitationReceived;
import com.cardgame.invitation.dto.InvitationUpdated;
import com.cardgame.presence.PlayerStatus;
import com.cardgame.presence.PresenceService;
import com.cardgame.presence.dto.OnlinePlayerDto;
import com.cardgame.realtime.Destinations;
import com.cardgame.realtime.RealtimePublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Invitations (rows 8-9, D7): only the selected online players, start when all accept, any decline cancels,
 * expiry after 60 s. In memory; every operation holds this service's lock.
 */
@Service
public class InvitationService {

    public static final Duration EXPIRY = Duration.ofSeconds(60);
    /** Closed invitations are kept this long so late answers get 409 instead of 404. */
    private static final Duration CLOSED_RETENTION = Duration.ofMinutes(10);

    private final Clock clock;
    private final TaskScheduler scheduler;
    private final RealtimePublisher publisher;
    private final PresenceService presence;
    private final GameLauncher gameLauncher;
    private final Map<Long, Invitation> invitations = new LinkedHashMap<>();
    private long nextId = 1;

    public InvitationService(Clock clock, @Qualifier("gameTaskScheduler") TaskScheduler scheduler,
                             RealtimePublisher publisher, PresenceService presence, GameLauncher gameLauncher) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.publisher = publisher;
        this.presence = presence;
        this.gameLauncher = gameLauncher;
    }

    public synchronized InvitationDto create(String inviter, CreateInvitationRequest request) {
        pruneClosed();
        List<String> requested = request.invitees() == null ? List.of() : request.invitees();
        validateNames(inviter, requested);
        GameSettings settings = toSettings(request.settings());

        if (presence.isInGame(inviter)) {
            throw new PlayerUnavailableException("Vous êtes déjà dans une partie.");
        }
        if (pendingInvolving(inviter)) {
            throw new PlayerUnavailableException("Vous avez déjà une invitation en attente.");
        }
        List<String> invitees = new ArrayList<>(requested.size());
        for (String name : requested) {
            OnlinePlayerDto online = presence.find(name).orElseThrow(
                    () -> new PlayerUnavailableException("Le joueur « %s » n'est pas connecté.".formatted(name)));
            if (online.status() == PlayerStatus.IN_GAME || presence.isInGame(online.username())) {
                throw new PlayerUnavailableException(
                        "Le joueur « %s » est déjà dans une partie.".formatted(online.username()));
            }
            if (pendingInvolving(online.username())) {
                throw new PlayerUnavailableException(
                        "Le joueur « %s » a déjà une invitation en attente.".formatted(online.username()));
            }
            invitees.add(online.username());
        }

        Instant now = clock.instant();
        Invitation invitation = new Invitation(nextId++, inviter, invitees, settings, now.plus(EXPIRY));
        invitations.put(invitation.id, invitation);
        invitation.expiry = scheduler.schedule(() -> expire(invitation.id), invitation.expiresAt);
        GameSettingsDto settingsDto = GameSettingsDto.from(settings);
        for (String invitee : invitees) {
            publisher.toUser(invitee, Destinations.INVITATIONS_QUEUE, new InvitationReceived(
                    invitation.id, inviter, invitation.invitees, settingsDto, invitation.expiresAt, now));
        }
        return toDto(invitation);
    }

    public synchronized InvitationDto accept(long invitationId, String username) {
        Invitation invitation = forInvitee(invitationId, username);
        String invitee = invitation.invitee(username).orElseThrow();
        if (invitation.accepted.contains(invitee)) {
            return toDto(invitation);
        }
        requirePending(invitation);
        invitation.accepted.add(invitee);
        invitation.accepted.sort((a, b) -> Integer.compare(invitation.invitees.indexOf(a), invitation.invitees.indexOf(b)));
        if (invitation.accepted.size() < invitation.invitees.size()) {
            notifyAll(invitation);
            return toDto(invitation);
        }
        invitation.status = InvitationStatus.ACCEPTED;
        cancelExpiry(invitation);
        notifyAll(invitation);
        invitation.gameId = gameLauncher.startGame(invitation.participants(), invitation.settings);
        return toDto(invitation);
    }

    public synchronized InvitationDto decline(long invitationId, String username) {
        Invitation invitation = forInvitee(invitationId, username);
        requirePending(invitation);
        invitation.status = InvitationStatus.DECLINED;
        invitation.declinedBy = invitation.invitee(username).orElseThrow();
        cancelExpiry(invitation);
        notifyAll(invitation);
        return toDto(invitation);
    }

    public synchronized List<InvitationDto> pendingFor(String username) {
        return invitations.values().stream()
                .filter(i -> i.status == InvitationStatus.PENDING && i.involves(username))
                .map(InvitationService::toDto)
                .toList();
    }

    private synchronized void expire(long invitationId) {
        Invitation invitation = invitations.get(invitationId);
        if (invitation == null || invitation.status != InvitationStatus.PENDING) {
            return;
        }
        invitation.status = InvitationStatus.EXPIRED;
        invitation.expiry = null;
        notifyAll(invitation);
    }

    private static void validateNames(String inviter, List<String> invitees) {
        if (invitees.isEmpty()) {
            throw new InvalidInvitationException("Invitez au moins un joueur.");
        }
        Set<String> seen = new HashSet<>();
        for (String name : invitees) {
            if (name == null || name.isBlank()) {
                throw new InvalidInvitationException("Le nom d'un invité est vide.");
            }
            if (Invitation.same(name, inviter)) {
                throw new InvalidInvitationException("Vous ne pouvez pas vous inviter vous-même.");
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new InvalidInvitationException("Le joueur « %s » est invité plusieurs fois.".formatted(name));
            }
        }
    }

    private static GameSettings toSettings(GameSettingsDto dto) {
        if (dto == null) {
            return GameSettings.defaults();
        }
        try {
            return dto.toSettings();
        } catch (IllegalArgumentException e) {
            throw new InvalidInvitationException(
                    "Paramètres invalides : 1 à 20 manches de 5 à 120 secondes.");
        }
    }

    private Invitation forInvitee(long invitationId, String username) {
        Invitation invitation = invitations.get(invitationId);
        if (invitation == null || invitation.invitee(username).isEmpty()) {
            throw new InvitationNotFoundException(invitationId);
        }
        return invitation;
    }

    private static void requirePending(Invitation invitation) {
        if (invitation.status != InvitationStatus.PENDING) {
            throw new InvitationClosedException(invitation.id, invitation.status);
        }
    }

    private boolean pendingInvolving(String username) {
        return invitations.values().stream()
                .anyMatch(i -> i.status == InvitationStatus.PENDING && i.involves(username));
    }

    private void notifyAll(Invitation invitation) {
        InvitationUpdated event = new InvitationUpdated(invitation.id, invitation.status,
                List.copyOf(invitation.accepted), invitation.declinedBy, clock.instant());
        invitation.participants().forEach(p -> publisher.toUser(p, Destinations.INVITATIONS_QUEUE, event));
    }

    private static void cancelExpiry(Invitation invitation) {
        if (invitation.expiry != null) {
            invitation.expiry.cancel(false);
            invitation.expiry = null;
        }
    }

    private void pruneClosed() {
        Instant cutoff = clock.instant().minus(CLOSED_RETENTION);
        invitations.values().removeIf(i -> i.status != InvitationStatus.PENDING && i.expiresAt.isBefore(cutoff));
    }

    private static InvitationDto toDto(Invitation i) {
        return new InvitationDto(i.id, i.inviter, i.invitees, List.copyOf(i.accepted),
                GameSettingsDto.from(i.settings), i.status, i.declinedBy, i.expiresAt, i.gameId);
    }
}
