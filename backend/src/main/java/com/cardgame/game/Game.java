package com.cardgame.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** A game (legacy {@code Partie}): created IN_PROGRESS when it starts, FINISHED at the end (D6, D22). */
@Entity
@Table(name = "games")
public class Game {

    /** Legacy {@code modeJeu} was always this constant. */
    public static final String NORMAL_MODE = "Normal";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private GameStatus status = GameStatus.IN_PROGRESS;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_rounds", nullable = false)
    private int totalRounds;

    @Column(name = "round_seconds", nullable = false)
    private int roundSeconds;

    @Column(name = "game_mode", nullable = false, length = 20)
    private String gameMode = NORMAL_MODE;

    protected Game() {
    }

    public Game(Instant startedAt, int totalRounds, int roundSeconds) {
        this.startedAt = startedAt;
        this.totalRounds = totalRounds;
        this.roundSeconds = roundSeconds;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public GameStatus getStatus() {
        return status;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    /** @throws IllegalStateException if already finished */
    public void finish(Instant at) {
        if (status == GameStatus.FINISHED) {
            throw new IllegalStateException("Game " + id + " is already finished");
        }
        this.status = GameStatus.FINISHED;
        this.finishedAt = at;
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
        return "Game{id=" + id + ", status=" + status + "}";
    }
}
