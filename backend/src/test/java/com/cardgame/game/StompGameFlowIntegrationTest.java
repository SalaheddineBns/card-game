package com.cardgame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.cardgame.SpaHttpClient;
import com.cardgame.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Two players over real HTTP + STOMP: log in, connect (CSRF header on CONNECT), invite, accept, GameStarted,
 * one correct claim → ScoresUpdated for both, a stale claim → ClaimRejected, logout closes the socket.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@Import(TestcontainersConfiguration.class)
class StompGameFlowIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @LocalServerPort
    int port;

    @Autowired
    SimpUserRegistry userRegistry;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        stompClient.setDefaultHeartbeat(new long[] {0, 0});
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void inviteAcceptPlayAClaimAndLogout() throws Exception {
        // given: two logged-in players with a STOMP connection each
        Player alice = Player.loggedIn(port, "Stomp_Alice");
        Player bob = Player.loggedIn(port, "stomp_bob");
        alice.connect(stompClient);
        bob.connect(stompClient);
        for (Player p : List.of(alice, bob)) {
            p.subscribe("/user/queue/invitations");
            p.subscribe("/user/queue/games");
            p.subscribe("/user/queue/errors");
        }
        awaitSubscriptions(alice.name, 3);
        awaitSubscriptions(bob.name, 3);

        // when: alice invites bob (name in another case), bob accepts
        ResponseEntity<String> created = alice.http.post("/api/invitations", """
                {"invitees":["STOMP_BOB"],"settings":{"totalRounds":1,"roundSeconds":120}}""");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long invitationId = ((Number) JsonPath.read(created.getBody(), "$.id")).longValue();
        Map<String, Object> received = bob.awaitEvent("/user/queue/invitations", "InvitationReceived");
        assertThat(received).containsEntry("inviter", "Stomp_Alice");
        assertThat((String) received.get("sentAt")).matches("\\d{4}-\\d\\d-\\d\\dT.*Z");

        ResponseEntity<String> accepted = bob.http.post("/api/invitations/" + invitationId + "/accept", null);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        long gameId = ((Number) JsonPath.read(accepted.getBody(), "$.gameId")).longValue();

        // then: both get GameStarted with that id
        for (Player p : List.of(alice, bob)) {
            Map<String, Object> started = p.awaitEvent("/user/queue/games", "GameStarted");
            assertThat(((Number) started.get("gameId")).longValue()).isEqualTo(gameId);
            p.subscribe("/topic/game/" + gameId);
        }
        awaitSubscriptions(alice.name, 4);
        awaitSubscriptions(bob.name, 4);

        // when: the player holding the center symbol claims SAME_SYMBOL on deal 1
        String snapshot = alice.http.get("/api/games/" + gameId).getBody();
        String centerSymbol = JsonPath.read(snapshot, "$.deal.centerCard.symbol");
        List<String> holders = JsonPath.read(snapshot, "$.deal.playerCards[?(@.symbol == '" + centerSymbol + "')].player");
        Player scorer = holders.getFirst().equals(alice.name) ? alice : bob;
        Player other = scorer == alice ? bob : alice;
        scorer.session.send("/app/game/" + gameId + "/claim", Map.of("claim", "SAME_SYMBOL", "dealVersion", 1));

        // then: both players see the new score and the next deal
        for (Player p : List.of(alice, bob)) {
            Map<String, Object> scores = p.awaitEvent("/topic/game/" + gameId, "ScoresUpdated");
            assertThat(scores).containsEntry("scoredBy", scorer.name);
            assertThat(JsonPath.<Integer>read(scores, "$.deal.dealVersion")).isEqualTo(2);
            assertThat(JsonPath.<List<Integer>>read(scores, "$.scores[?(@.player == '" + scorer.name + "')].score"))
                    .containsExactly(1);
        }

        // and a claim on the old deal is rejected for the sender only
        other.session.send("/app/game/" + gameId + "/claim", Map.of("claim", "BOTH", "dealVersion", 1));
        Map<String, Object> rejected = other.awaitEvent("/user/queue/errors", "ClaimRejected");
        assertThat(rejected).containsEntry("reason", "STALE").containsEntry("currentDealVersion", 2);

        // and a claim for a game that is not live answers an Error (ProblemDetail-like) to the sender
        other.session.send("/app/game/999999/claim", Map.of("claim", "BOTH", "dealVersion", 1));
        Map<String, Object> error = other.awaitEvent("/user/queue/errors", "Error");
        assertThat(error).containsEntry("status", 404).containsEntry("title", "Partie introuvable");

        // when alice logs out, her STOMP connection is closed (row 3)
        assertThat(alice.http.post("/api/auth/logout", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        await().atMost(TIMEOUT).until(() -> !alice.session.isConnected() && userRegistry.getUser(alice.name) == null);
        assertThat(bob.session.isConnected()).isTrue();
    }

    @Test
    void connectWithoutTheCsrfHeaderIsRefused() {
        Player carol = Player.loggedIn(port, "stomp_carol");

        assertThatThrownBy(() -> carol.connect(stompClient, false)).isNotNull();
        assertThat(userRegistry.getUser("stomp_carol")).isNull();
    }

    @Test
    void anonymousHandshakeIsRefused() {
        SpaHttpClient anonymous = new SpaHttpClient(port);
        anonymous.get("/api/auth/me");
        Player nobody = new Player("nobody", anonymous);

        assertThatThrownBy(() -> nobody.connect(stompClient)).isNotNull();
    }

    @Test
    void subscribingToAGameYouAreNotInIsRefusedWithAnErrorFrame() throws Exception {
        Player dave = Player.loggedIn(port, "stomp_dave");
        dave.connect(stompClient);

        dave.subscribe("/topic/game/424242");

        await().atMost(TIMEOUT).until(() -> dave.errorFrames.get() > 0);
        assertThat(userRegistry.findSubscriptions(s -> s.getDestination().equals("/topic/game/424242"))).isEmpty();
    }

    private void awaitSubscriptions(String username, int count) {
        await().atMost(TIMEOUT).until(() -> userRegistry.findSubscriptions(
                s -> s.getSession().getUser().getName().equals(username)).size() >= count);
    }

    /** One browser: HTTP client with cookies + one STOMP session. */
    private static final class Player {

        final String name;
        final SpaHttpClient http;
        final List<Received> events = new CopyOnWriteArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger errorFrames = new java.util.concurrent.atomic.AtomicInteger();
        StompSession session;

        record Received(String destination, Map<String, Object> payload) {
        }

        Player(String name, SpaHttpClient http) {
            this.name = name;
            this.http = http;
        }

        static Player loggedIn(int port, String name) {
            SpaHttpClient http = new SpaHttpClient(port);
            http.get("/api/auth/me");
            assertThat(http.post("/api/auth/register", """
                    {"username":"%s","password":"password123","age":30}""".formatted(name)).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
            assertThat(http.post("/api/auth/login", """
                    {"username":"%s","password":"password123"}""".formatted(name)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            return new Player(name, http);
        }

        void connect(WebSocketStompClient client) throws Exception {
            connect(client, true);
        }

        void connect(WebSocketStompClient client, boolean withCsrf) throws Exception {
            WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
            StringBuilder cookies = new StringBuilder();
            http.cookie("JSESSIONID").ifPresent(v -> cookies.append("JSESSIONID=").append(v).append("; "));
            http.cookie("XSRF-TOKEN").ifPresent(v -> cookies.append("XSRF-TOKEN=").append(v));
            handshake.add("Cookie", cookies.toString());
            StompHeaders connect = new StompHeaders();
            if (withCsrf) {
                connect.add("X-XSRF-TOKEN", http.cookie("XSRF-TOKEN").orElseThrow());
            }
            session = client.connectAsync(http.baseUrl().replace("http", "ws") + "/ws", handshake, connect,
                    new StompSessionHandlerAdapter() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return byte[].class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            errorFrames.incrementAndGet();   // only ERROR frames reach the session handler
                        }

                        @Override
                        public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                                    byte[] payload, Throwable exception) {
                            // an ERROR frame whose text payload could not be converted lands here (command null)
                            errorFrames.incrementAndGet();
                        }

                        @Override
                        public void handleTransportError(StompSession s, Throwable exception) {
                            errorFrames.incrementAndGet();
                        }
                    }).get(10, TimeUnit.SECONDS);
        }

        void subscribe(String destination) {
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    events.add(new Received(destination, (Map<String, Object>) payload));
                }
            });
        }

        Map<String, Object> awaitEvent(String destination, String type) {
            return await().atMost(TIMEOUT).until(() -> events.stream()
                    .filter(e -> e.destination().equals(destination) && type.equals(e.payload().get("type")))
                    .map(Received::payload)
                    .findFirst()
                    .orElse(null), java.util.Objects::nonNull);
        }
    }
}
