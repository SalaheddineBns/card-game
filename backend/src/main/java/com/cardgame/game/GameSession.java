package com.cardgame.game;

import com.cardgame.game.engine.GameState;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

/**
 * One live game. Mutable; every read or write must hold this object's monitor ({@code synchronized (session)}),
 * so claims and timer ticks of one game are applied one at a time (D8).
 */
final class GameSession {

    private final long gameId;
    private volatile GameState state;
    private ScheduledFuture<?> timer;
    private boolean over;

    GameSession(long gameId, GameState state) {
        this.gameId = gameId;
        this.state = state;
    }

    long gameId() {
        return gameId;
    }

    GameState state() {
        return state;
    }

    void state(GameState state) {
        this.state = state;
    }

    ScheduledFuture<?> timer() {
        return timer;
    }

    void timer(ScheduledFuture<?> timer) {
        this.timer = timer;
    }

    boolean over() {
        return over;
    }

    void markOver() {
        this.over = true;
    }

    /** The player's canonical spelling, matched ignoring case (D18). Players never change, no lock needed. */
    Optional<String> player(String username) {
        String wanted = username.toLowerCase(Locale.ROOT);
        return state.players().stream().filter(p -> p.toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }
}
