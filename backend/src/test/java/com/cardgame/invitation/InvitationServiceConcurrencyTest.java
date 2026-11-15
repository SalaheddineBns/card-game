package com.cardgame.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.cardgame.game.GameLauncher;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.invitation.dto.InvitationDto;
import com.cardgame.presence.PresenceService;
import com.cardgame.realtime.RealtimePublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

/** G9: starting the game does not hold the invitation lock, and the participants stay busy meanwhile. */
class InvitationServiceConcurrencyTest {

    @Test
    void otherInvitationOperationsDoNotWaitForTheGameStart() throws Exception {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        doReturn(mock(ScheduledFuture.class)).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        PresenceService presence = new PresenceService(clock, mock(RealtimePublisher.class));
        List.of("alice", "bob", "carol", "dave").forEach(n -> presence.connected(n, n + "-s"));
        CountDownLatch inStartGame = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GameLauncher slowLauncher = (players, settings) -> {
            inStartGame.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 77L;
        };
        InvitationService invitations = new InvitationService(clock, scheduler, mock(RealtimePublisher.class),
                presence, slowLauncher);
        InvitationDto invitation = invitations.create("alice", new CreateInvitationRequest(List.of("bob"), null));

        // when: bob's accept is inside startGame
        CompletableFuture<InvitationDto> accepted =
                CompletableFuture.supplyAsync(() -> invitations.accept(invitation.id(), "bob"));
        assertThat(inStartGame.await(5, TimeUnit.SECONDS)).isTrue();

        // then: other operations run at once, and alice/bob can't be invited while their game starts
        InvitationDto other = CompletableFuture
                .supplyAsync(() -> invitations.create("carol", new CreateInvitationRequest(List.of("dave"), null)))
                .get(2, TimeUnit.SECONDS);
        assertThat(other.status()).isEqualTo(InvitationStatus.PENDING);
        assertThatThrownBy(() -> invitations.create("dave", new CreateInvitationRequest(List.of("bob"), null)))
                .isInstanceOf(PlayerUnavailableException.class);

        release.countDown();
        assertThat(accepted.get(5, TimeUnit.SECONDS).gameId()).isEqualTo(77L);
    }
}
