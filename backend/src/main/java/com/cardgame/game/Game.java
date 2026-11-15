package com.cardgame.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** A finished game (legacy {@code Partie}). One row per game (D6). */
@Entity
@Table(name = "games")
public class Game {

    /** Legacy {@code modeJeu} was always this constant. */
    public static final String NORMAL_MODE = "Normal";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @Column(name = "total_rounds", nullable = false)
    private int totalRounds;

    @Column(name = "round_seconds", nullable = false)
    private int roundSeconds;

    @Column(name = "game_mode", nullable = false, length = 20)
    private String gameMode = NORMAL_MODE;

    protected Game() {
    }

    public Game(Instant playedAt, int totalRounds, int roundSeconds) {
        this.playedAt = playedAt;
        this.totalRounds = totalRounds;
        this.roundSeconds = roundSeconds;
    }

    public Long getId() {
        return id;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public int getRoundSeconds() {
        return roundSeconds;
    }

    public String getGameMode() {
        return gameMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Game other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Game{id=" + id + ", playedAt=" + playedAt + "}";
    }
}
