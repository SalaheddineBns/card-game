package com.cardgame.player;

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
import org.hibernate.annotations.CreationTimestamp;

/** A registered account (legacy {@code Joueur}). */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 20)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "age", nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed;

    @Column(name = "victories", nullable = false)
    private int victories;

    @Column(name = "average_score", nullable = false)
    private double averageScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Player() {
    }

    public Player(String username, String passwordHash, int age, Gender gender) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.age = age;
        this.gender = gender;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public int getVictories() {
        return victories;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setStats(int gamesPlayed, int victories, double averageScore) {
        this.gamesPlayed = gamesPlayed;
        this.victories = victories;
        this.averageScore = averageScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Player other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Player{id=" + id + ", username='" + username + "'}";
    }
}
