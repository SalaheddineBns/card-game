package com.cardgame.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/** A player's final score in one game (legacy {@code Participation}). */
@Entity
@Table(name = "participations")
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** Id of the player (feature boundary: the game feature does not map the player entity). */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "score", nullable = false)
    private int score;

    protected Participation() {
    }

    public Participation(Game game, Long playerId, int score) {
        this.game = game;
        this.playerId = playerId;
        this.score = score;
    }

    public Long getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Participation other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Participation{id=" + id + ", score=" + score + "}";
    }
}
