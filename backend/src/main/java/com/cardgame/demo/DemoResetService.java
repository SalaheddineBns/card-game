package com.cardgame.demo;

import com.cardgame.auth.HttpSessionTracker;
import com.cardgame.game.GameService;
import com.cardgame.invitation.InvitationService;
import com.cardgame.player.PlayerService;
import com.cardgame.player.dto.NewPlayer;
import com.cardgame.presence.PresenceService;
import com.cardgame.realtime.RealtimeSessionRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D29: puts the public demo back to a clean state: nobody logged in, no game, only the demo players.
 *
 * <p>This is deliberately cross-cutting (it empties every feature's tables and in-memory state). It uses each
 * feature's service for the in-memory part, and one SQL TRUNCATE for the tables.
 */
@Service
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    private final DemoResetProperties properties;
    private final HttpSessionTracker httpSessions;
    private final RealtimeSessionRegistry realtimeSessions;
    private final GameService games;
    private final InvitationService invitations;
    private final PresenceService presence;
    private final PlayerService players;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public DemoResetService(DemoResetProperties properties, HttpSessionTracker httpSessions,
                            RealtimeSessionRegistry realtimeSessions, GameService games,
                            InvitationService invitations, PresenceService presence, PlayerService players,
                            PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
                            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.httpSessions = httpSessions;
        this.realtimeSessions = realtimeSessions;
        this.games = games;
        this.invitations = invitations;
        this.presence = presence;
        this.players = players;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** What one reset did. */
    public record Result(int sockets, int httpSessions, int liveGames, int pendingInvitations, int onlinePlayers,
                         long deletedPlayers, long deletedGames, int demoPlayers) {
    }

    /**
     * 1. close every STOMP connection and invalidate every HTTP session; 2. clear live games (timers cancelled),
     * invitations (expiry tasks cancelled) and presence; 3. in one transaction, empty participations, games and
     * players (ids restart at 1) and recreate the demo players with zero stats; 4. log one INFO line.
     */
    public Result reset() {
        int sockets = realtimeSessions.closeEverything();
        int sessions = httpSessions.invalidateAll();

        int liveGames = games.discardLiveGames();
        int pending = invitations.clearAll();
        int online = presence.clear();

        long[] deleted = new long[2];
        Integer created = transaction.execute(status -> {
            deleted[0] = count("players");
            deleted[1] = count("games");
            jdbc.execute("truncate table participations, games, players restart identity cascade");
            return createDemoPlayers();
        });

        Result result = new Result(sockets, sessions, liveGames, pending, online, deleted[0], deleted[1],
                created == null ? 0 : created);
        log.info("Demo reset: closed {} STOMP connection(s) and {} HTTP session(s); dropped {} live game(s), "
                        + "{} pending invitation(s), {} online player(s); deleted {} player(s) and {} game(s); "
                        + "recreated demo players {}",
                result.sockets(), result.httpSessions(), result.liveGames(), result.pendingInvitations(),
                result.onlinePlayers(), result.deletedPlayers(), result.deletedGames(), properties.demoUsers());
        return result;
    }

    /**
     * Startup: creates the demo players that don't exist yet, whatever other players are in the table.
     * Existing players (demo or not) are left untouched. @return players created
     */
    public int seedMissingDemoPlayers() {
        Integer created = transaction.execute(status -> createDemoPlayers(
                properties.demoUsers().stream().filter(u -> players.findCredentials(u).isEmpty()).toList()));
        int count = created == null ? 0 : created;
        if (count > 0) {
            log.info("Demo seed: created {} missing demo player(s)", count);
        }
        return count;
    }

    private int createDemoPlayers() {
        return createDemoPlayers(properties.demoUsers());
    }

    private int createDemoPlayers(List<String> usernames) {
        for (String username : usernames) {
            // each player gets its own salt
            players.create(new NewPlayer(username, passwordEncoder.encode(properties.demoPassword()), 30, null));
        }
        return usernames.size();
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }
}
