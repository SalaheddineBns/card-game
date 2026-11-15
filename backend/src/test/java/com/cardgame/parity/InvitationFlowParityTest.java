package com.cardgame.parity;

import static com.cardgame.realtime.Destinations.INVITATIONS_QUEUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardgame.common.DomainException;
import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.invitation.InvalidInvitationException;
import com.cardgame.invitation.InvitationClosedException;
import com.cardgame.invitation.InvitationNotFoundException;
import com.cardgame.invitation.InvitationService;
import com.cardgame.invitation.InvitationStatus;
import com.cardgame.invitation.PlayerUnavailableException;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.invitation.dto.InvitationDto;
import com.cardgame.invitation.dto.InvitationReceived;
import com.cardgame.invitation.dto.InvitationUpdated;
import com.cardgame.presence.PresenceService;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

/**
 * Invitation flow (plan rows 8-9). Contract: docs/migration-plan.md, "Contract for backend: invitation and
 * game services" → Invitations.
 *
 * <p>legacy: legacy/src/main/java/webSocket/UserStatusWebSocket.java, handleInvite lines 278-304,
 * handleResponse lines 306-321; legacy/src/main/webApp/home.jsp, sendInvitation lines 178-201 and the
 * {@code invitation} handler lines 154-163 ({@code confirm()} → accept / reject).
 *
 * <p>Legacy behaviour kept: the inviter is a player; the game starts only when <em>every</em> invitee has
 * accepted; the start carries the inviter's settings. Everything else below is a DECISION (D7, D10, D16).
 */
@Disabled("pending port: invitations (plan rows 8-9) - implement com.cardgame.invitation.InvitationService")
class InvitationFlowParityTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final GameSettingsDto SETTINGS = new GameSettingsDto(5, 20);

    private MutableClock clock;
    private FakeTaskScheduler scheduler;
    private RecordingPublisher publisher;
    private PresenceService presence;
    private FakeGameLauncher launcher;
    private InvitationService invitations;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        scheduler = new FakeTaskScheduler(clock);
        publisher = new RecordingPublisher();
        presence = new PresenceService(clock, publisher);
        launcher = new FakeGameLauncher();
        invitations = new InvitationService(clock, scheduler, publisher, presence, launcher);
        for (String name : List.of("alice", "bob", "carol", "dave")) {
            presence.connected(name, name + "-tab1");
        }
        publisher.clear();
    }

    // DECISION Q7: legacy ignored the "invitees" field and sent the invitation to every other open socket
    // (handleInvite lines 292-300). Now only the selected players are invited.
    @Test
    void invitesOnlyTheSelectedPlayers() {
        // when
        InvitationDto dto = invitations.create("alice", request(SETTINGS, "bob", "carol"));

        // then
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.inviter()).isEqualTo("alice");
        assertThat(dto.invitees()).containsExactly("bob", "carol");
        assertThat(dto.accepted()).isEmpty();
        assertThat(dto.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(dto.settings()).isEqualTo(SETTINGS);
        assertThat(dto.declinedBy()).isNull();
        assertThat(dto.gameId()).isNull();
        assertThat(dto.expiresAt()).isEqualTo(T0.plusSeconds(60));

        assertThat(publisher.recipientsOf(INVITATIONS_QUEUE, InvitationReceived.class))
                .containsExactlyInAnyOrder("bob", "carol");
        InvitationReceived received = publisher.toUser("bob", INVITATIONS_QUEUE, InvitationReceived.class).getFirst();
        assertThat(received.invitationId()).isEqualTo(1L);
        assertThat(received.inviter()).isEqualTo("alice");
        assertThat(received.invitees()).containsExactly("bob", "carol");
        assertThat(received.settings()).isEqualTo(SETTINGS);
        assertThat(received.expiresAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(received.sentAt()).isEqualTo(T0);
        // the inviter gets the HTTP response, dave was not selected
        assertThat(publisher.toUser("alice", INVITATIONS_QUEUE, InvitationReceived.class)).isEmpty();
        assertThat(publisher.toUser("dave", INVITATIONS_QUEUE, InvitationReceived.class)).isEmpty();
    }

    @Test
    void inviteeNamesAreMatchedIgnoringCaseAndStoredWithTheirRegisteredSpelling() {
        InvitationDto dto = invitations.create("alice", request(SETTINGS, "BOB", "Carol"));

        assertThat(dto.invitees()).containsExactly("bob", "carol");
    }

    @Test
    void missingSettingsUseTheDefaults() {
        // DECISION Q10: defaults are 3 rounds of 10 s
        InvitationDto dto = invitations.create("alice", new CreateInvitationRequest(List.of("bob"), null));

        assertThat(dto.settings()).isEqualTo(GameSettingsDto.from(GameSettings.defaults()));
    }

    @ParameterizedTest(name = "invitees [{0}] -> 400")
    @CsvSource({
            "alice",          // the inviter can't invite themselves
            "ALICE",          // ... ignoring case
            "bob|alice",
            "bob|bob",        // duplicates
            "bob|Bob",        // duplicates ignoring case
    })
    void rejectsSelfInvitationsAndDuplicates(String invitees) {
        assertThatThrownBy(() -> invitations.create("alice", request(SETTINGS, invitees.split("\\|"))))
                .isInstanceOf(InvalidInvitationException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertNothingHappened();
    }

    @Test
    void rejectsAnEmptyInviteeList() {
        assertThatThrownBy(() -> invitations.create("alice", new CreateInvitationRequest(List.of(), SETTINGS)))
                .isInstanceOf(InvalidInvitationException.class);
        assertNothingHappened();
    }

    @Test
    void rejectsAnOfflineInvitee() {
        // zoe is not connected (D7: only online players can be invited)
        assertThatThrownBy(() -> invitations.create("alice", request(SETTINGS, "bob", "zoe")))
                .isInstanceOf(PlayerUnavailableException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.CONFLICT));
        assertNothingHappened();
    }

    // DECISION Q16: legacy also invited players who were on a game page (they never answered).
    @Test
    void rejectsAnInviteeWhoIsInAGame() {
        presence.setInGame(List.of("bob"), true);
        publisher.clear();

        assertThatThrownBy(() -> invitations.create("alice", request(SETTINGS, "bob")))
                .isInstanceOf(PlayerUnavailableException.class);
        assertNothingHappened();
    }

    @Test
    void rejectsAnInviterWhoIsInAGame() {
        presence.setInGame(List.of("alice"), true);
        publisher.clear();

        assertThatThrownBy(() -> invitations.create("alice", request(SETTINGS, "bob")))
                .isInstanceOf(PlayerUnavailableException.class);
        assertNothingHappened();
    }

    @Test
    void rejectsPlayersWhoAreAlreadyInAPendingInvitation() {
        invitations.create("alice", request(SETTINGS, "bob"));
        publisher.clear();
        int tasksBefore = scheduler.pendingTaskTimes().size();

        // bob is an invitee of a pending invitation
        assertThatThrownBy(() -> invitations.create("carol", request(SETTINGS, "bob")))
                .isInstanceOf(PlayerUnavailableException.class);
        // alice is the inviter of a pending invitation (as invitee)
        assertThatThrownBy(() -> invitations.create("carol", request(SETTINGS, "alice")))
                .isInstanceOf(PlayerUnavailableException.class);
        // alice is the inviter of a pending invitation (as inviter)
        assertThatThrownBy(() -> invitations.create("alice", request(SETTINGS, "dave")))
                .isInstanceOf(PlayerUnavailableException.class);
        // bob is an invitee of a pending invitation (as inviter)
        assertThatThrownBy(() -> invitations.create("bob", request(SETTINGS, "dave")))
                .isInstanceOf(PlayerUnavailableException.class);

        assertThat(publisher.all()).isEmpty();
        assertThat(scheduler.pendingTaskTimes()).hasSize(tasksBefore);
    }

    @Test
    void eachAcceptanceIsReportedToEveryParticipantWhileOthersHaveNotAnswered() {
        InvitationDto created = invitations.create("alice", request(SETTINGS, "bob", "carol"));
        publisher.clear();
        clock.advance(Duration.ofSeconds(5));

        InvitationDto dto = invitations.accept(created.id(), "bob");

        assertThat(dto.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(dto.accepted()).containsExactly("bob");
        assertThat(publisher.recipientsOf(INVITATIONS_QUEUE, InvitationUpdated.class))
                .containsExactlyInAnyOrder("alice", "bob", "carol");
        InvitationUpdated update = publisher.toUser("alice", INVITATIONS_QUEUE, InvitationUpdated.class).getFirst();
        assertThat(update.invitationId()).isEqualTo(created.id());
        assertThat(update.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(update.accepted()).containsExactly("bob");
        assertThat(update.declinedBy()).isNull();
        assertThat(update.sentAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(launcher.launches()).isEmpty();
    }

    @Test
    void whenEveryInviteeAcceptsTheGameStartsWithTheInviterFirst() {
        // given
        InvitationDto created = invitations.create("alice", request(SETTINGS, "carol", "bob"));
        invitations.accept(created.id(), "bob");
        publisher.clear();

        // when
        InvitationDto dto = invitations.accept(created.id(), "carol");

        // then: legacy beginGame (lines 379-432) started the game when accepted == invited
        assertThat(dto.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(dto.accepted()).containsExactly("carol", "bob");   // invitation order
        assertThat(launcher.launches()).singleElement().satisfies(launch -> {
            assertThat(launch.players()).containsExactly("alice", "carol", "bob");
            assertThat(launch.settings()).isEqualTo(SETTINGS.toSettings());
        });
        assertThat(dto.gameId()).isEqualTo(launcher.launches().getFirst().gameId());
        assertThat(publisher.recipientsOf(INVITATIONS_QUEUE, InvitationUpdated.class))
                .containsExactlyInAnyOrder("alice", "bob", "carol");
        assertThat(publisher.toUser("bob", INVITATIONS_QUEUE, InvitationUpdated.class).getFirst().status())
                .isEqualTo(InvitationStatus.ACCEPTED);
        // the expiry task is cancelled and later ticks change nothing
        assertThat(scheduler.pendingTaskTimes()).isEmpty();
        publisher.clear();
        scheduler.advanceAndRun(Duration.ofSeconds(120));
        assertThat(publisher.all()).isEmpty();
        assertThat(invitations.pendingFor("alice")).isEmpty();
    }

    @Test
    void acceptingTwiceChangesNothing() {
        InvitationDto created = invitations.create("alice", request(SETTINGS, "bob", "carol"));
        InvitationDto first = invitations.accept(created.id(), "bob");
        publisher.clear();

        InvitationDto second = invitations.accept(created.id(), "BOB");

        assertThat(second).isEqualTo(first);
        assertThat(publisher.all()).isEmpty();
    }

    // DECISION Q7: legacy ignored "reject" (handleResponse lines 306-321): the game never started and the
    // inviter was never told. Now any decline cancels the invitation and notifies everyone.
    @Test
    void anyDeclineCancelsTheInvitationAndNotifiesEveryone() {
        // given
        InvitationDto created = invitations.create("alice", request(SETTINGS, "bob", "carol"));
        invitations.accept(created.id(), "bob");
        publisher.clear();

        // when
        InvitationDto dto = invitations.decline(created.id(), "carol");

        // then
        assertThat(dto.status()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(dto.declinedBy()).isEqualTo("carol");
        assertThat(publisher.recipientsOf(INVITATIONS_QUEUE, InvitationUpdated.class))
                .containsExactlyInAnyOrder("alice", "bob", "carol");
        InvitationUpdated update = publisher.toUser("alice", INVITATIONS_QUEUE, InvitationUpdated.class).getFirst();
        assertThat(update.status()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(update.declinedBy()).isEqualTo("carol");
        assertThat(launcher.launches()).isEmpty();
        assertThat(scheduler.pendingTaskTimes()).isEmpty();

        // and the invitation is closed
        assertThatThrownBy(() -> invitations.accept(created.id(), "carol"))
                .isInstanceOf(InvitationClosedException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.CONFLICT));

        // and everybody is free for a new invitation at once
        InvitationDto again = invitations.create("bob", request(SETTINGS, "alice", "carol"));
        assertThat(again.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void aPendingInvitationExpiresAfterSixtySeconds() {
        // given
        InvitationDto created = invitations.create("alice", request(SETTINGS, "bob", "carol"));
        invitations.accept(created.id(), "bob");
        publisher.clear();

        // when: 59 s → nothing
        scheduler.advanceAndRun(Duration.ofSeconds(59));
        assertThat(publisher.all()).isEmpty();
        assertThat(invitations.pendingFor("carol")).extracting(InvitationDto::id).containsExactly(created.id());

        // when: 60 s
        scheduler.advanceAndRun(Duration.ofSeconds(1));

        // then
        assertThat(publisher.recipientsOf(INVITATIONS_QUEUE, InvitationUpdated.class))
                .containsExactlyInAnyOrder("alice", "bob", "carol");
        InvitationUpdated update = publisher.toUser("carol", INVITATIONS_QUEUE, InvitationUpdated.class).getFirst();
        assertThat(update.status()).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(update.sentAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(launcher.launches()).isEmpty();
        assertThat(invitations.pendingFor("alice")).isEmpty();
        assertThatThrownBy(() -> invitations.accept(created.id(), "carol"))
                .isInstanceOf(InvitationClosedException.class);
        assertThatThrownBy(() -> invitations.decline(created.id(), "carol"))
                .isInstanceOf(InvitationClosedException.class);
        assertThat(invitations.create("carol", request(SETTINGS, "alice")).status())
                .isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void onlyInviteesCanAnswerAnInvitation() {
        InvitationDto created = invitations.create("alice", request(SETTINGS, "bob"));
        publisher.clear();

        // dave was not invited, alice is the inviter, 999 does not exist: all look like "not found"
        assertThatThrownBy(() -> invitations.accept(created.id(), "dave"))
                .isInstanceOf(InvitationNotFoundException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> invitations.decline(created.id(), "dave"))
                .isInstanceOf(InvitationNotFoundException.class);
        assertThatThrownBy(() -> invitations.accept(created.id(), "alice"))
                .isInstanceOf(InvitationNotFoundException.class);
        assertThatThrownBy(() -> invitations.accept(999L, "bob"))
                .isInstanceOf(InvitationNotFoundException.class);
        assertThat(publisher.all()).isEmpty();
    }

    @Test
    void pendingInvitationsAreListedForTheInviterAndTheInviteesOnly() {
        InvitationDto created = invitations.create("alice", request(SETTINGS, "bob"));

        assertThat(invitations.pendingFor("alice")).extracting(InvitationDto::id).containsExactly(created.id());
        assertThat(invitations.pendingFor("Bob")).extracting(InvitationDto::id).containsExactly(created.id());
        assertThat(invitations.pendingFor("carol")).isEmpty();
    }

    private void assertNothingHappened() {
        assertThat(publisher.all()).isEmpty();
        assertThat(scheduler.pendingTaskTimes()).isEmpty();
        assertThat(launcher.launches()).isEmpty();
        assertThat(invitations.pendingFor("alice")).isEmpty();
    }

    private static CreateInvitationRequest request(GameSettingsDto settings, String... invitees) {
        return new CreateInvitationRequest(Arrays.asList(invitees), settings);
    }
}
