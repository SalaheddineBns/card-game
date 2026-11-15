package com.cardgame.game.engine;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.random.RandomGenerator;

/**
 * Pure game rules. Stateless apart from the injected random generator and clock: every method takes a
 * {@link GameState} and returns a new one. Callers must apply transitions for one game one at a time
 * (so that "first correct claim per deal version" holds).
 */
public final class GameEngine {

    private static final int MIN_PLAYERS = 2;

    private final CardDealer dealer;
    private final Clock clock;

    public GameEngine(RandomGenerator random, Clock clock) {
        this.dealer = new CardDealer(Objects.requireNonNull(random, "random"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Starts a game at round 1 with zero scores in the given player order.
     *
     * @throws IllegalArgumentException for fewer than 2 players or duplicate names (D7)
     */
    public GameState start(List<String> players, GameSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (players.size() < MIN_PLAYERS) {
            throw new IllegalArgumentException("A game needs at least " + MIN_PLAYERS + " players");
        }
        if (new HashSet<>(players).size() != players.size()) {
            throw new IllegalArgumentException("Duplicate players: " + players);
        }
        SequencedMap<String, Integer> scores = new LinkedHashMap<>();
        players.forEach(p -> scores.put(p, 0));
        return new GameState(players, settings, GamePhase.IN_PROGRESS, 1, scores, dealer.deal(players), 1,
                clock.instant());
    }

    /**
     * Judges a claim (D8).
     *
     * @throws IllegalStateException if the game is not in progress
     * @throws IllegalArgumentException if {@code player} is not in the game
     */
    public ClaimOutcome claim(GameState state, String player, Claim claim, int dealVersion) {
        requireInProgress(state);
        Objects.requireNonNull(claim, "claim");
        if (!state.scores().containsKey(player)) {
            throw new IllegalArgumentException("Player is not in this game: " + player);
        }
        if (dealVersion != state.dealVersion()) {
            return new ClaimOutcome(ClaimStatus.STALE, state);
        }
        Deal deal = state.deal();
        if (!ClaimRules.isCorrect(claim, deal.cardOf(player), deal.centerCard())) {
            return new ClaimOutcome(ClaimStatus.WRONG, state);
        }
        SequencedMap<String, Integer> scores = new LinkedHashMap<>(state.scores());
        scores.merge(player, ClaimRules.pointsFor(claim, deal.cardOf(player), deal.centerCard()), Integer::sum);
        GameState next = new GameState(state.players(), state.settings(), state.phase(), state.currentRound(),
                scores, dealer.deal(state.players()), state.dealVersion() + 1, state.roundStartedAt());
        return new ClaimOutcome(ClaimStatus.CORRECT, next);
    }

    /**
     * Moves to the next round with a new deal, or finishes the game after the last round.
     *
     * @throws IllegalStateException if the game is not in progress
     */
    public GameState roundTimerExpired(GameState state) {
        requireInProgress(state);
        if (state.currentRound() < state.settings().totalRounds()) {
            return new GameState(state.players(), state.settings(), GamePhase.IN_PROGRESS,
                    state.currentRound() + 1, state.scores(), dealer.deal(state.players()),
                    state.dealVersion() + 1, clock.instant());
        }
        return new GameState(state.players(), state.settings(), GamePhase.FINISHED, state.currentRound(),
                state.scores(), state.deal(), state.dealVersion(), state.roundStartedAt());
    }

    /**
     * @throws IllegalStateException unless the game is {@link GamePhase#FINISHED}
     */
    public GameResult result(GameState state) {
        if (state.phase() != GamePhase.FINISHED) {
            throw new IllegalStateException("Game is not finished");
        }
        return determineResult(state.scores());
    }

    /** Winner, loser and draw rules (D3, D4, D5). Lists keep the map's order. */
    public static GameResult determineResult(SequencedMap<String, Integer> scores) {
        if (scores.isEmpty()) {
            throw new IllegalArgumentException("No scores");
        }
        int top = Collections.max(scores.values());
        int bottom = Collections.min(scores.values());
        List<String> leaders = new ArrayList<>();
        List<String> trailers = new ArrayList<>();
        scores.forEach((player, score) -> {
            if (score == top) {
                leaders.add(player);
            }
            if (top != bottom && score == bottom) {
                trailers.add(player);
            }
        });
        String winner = leaders.size() == 1 ? leaders.getFirst() : null;
        String loser = trailers.size() == 1 ? trailers.getFirst() : null;
        List<PlayerOutcome> outcomes = new ArrayList<>(scores.size());
        scores.forEach((player, score) -> outcomes.add(new PlayerOutcome(player, score, player.equals(winner))));
        return new GameResult(winner, loser, leaders, top, trailers, bottom, outcomes);
    }

    private static void requireInProgress(GameState state) {
        if (state.phase() != GamePhase.IN_PROGRESS) {
            throw new IllegalStateException("Game is not in progress");
        }
    }
}
