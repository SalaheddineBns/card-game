package com.cardgame.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cardgame.common.DomainException;
import com.cardgame.game.GameLauncher;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.presence.PresenceService;
import com.cardgame.realtime.RealtimePublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;

/** D26: inviting needs an open lobby (STOMP) connection. */
class InvitationServiceTest {

    private RealtimePublisher publisher;
    private TaskScheduler scheduler;
    private PresenceService presence;
    private InvitationService invitations;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        publisher = mock(RealtimePublisher.class);
        scheduler = mock(TaskScheduler.class);
        doReturn(mock(ScheduledFuture.class)).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        presence = new PresenceService(clock, mock(RealtimePublisher.class));
        invitations = new InvitationService(clock, scheduler, publisher, presence, mock(GameLauncher.class));
        presence.connected("bob", "bob-s1");
    }

    @Test
    void anInviterWithoutAnyStompConnectionGets409AndNothingHappens() {
        assertThatThrownBy(() -> invitations.create("alice", new CreateInvitationRequest(List.of("bob"), null)))
                .isInstanceOf(InviterNotConnectedException.class)
                .satisfies(e -> {
                    DomainException domain = (DomainException) e;
                    assertThat(domain.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(domain.getMessage()).isEqualTo("Vous devez être connecté au lobby pour inviter des joueurs.");
                });
        verifyNoInteractions(publisher);
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(invitations.pendingFor("bob")).isEmpty();
    }

    @Test
    void theInviterMayInviteOnceConnectedAndIsRejectedAgainAfterTheLastTabCloses() {
        presence.connected("Alice", "alice-s1");
        assertThat(invitations.create("alice", new CreateInvitationRequest(List.of("bob"), null)).status())
                .isEqualTo(InvitationStatus.PENDING);

        presence.connected("carol", "carol-s1");
        presence.disconnected("Alice", "alice-s1");
        assertThatThrownBy(() -> invitations.create("alice", new CreateInvitationRequest(List.of("carol"), null)))
                .isInstanceOf(InviterNotConnectedException.class);
    }

    @Test
    void invalidInviteeListsAreStill400EvenWhenTheInviterIsOffline() {
        assertThatThrownBy(() -> invitations.create("alice", new CreateInvitationRequest(List.of(), null)))
                .isInstanceOf(InvalidInvitationException.class);
    }
}
