package com.cardgame.game.engine;

/** A player's lifetime statistics, updated after every game (D6). */
public record PlayerStats(int gamesPlayed, int victories, double averageScore) {

    public static PlayerStats initial() {
        return new PlayerStats(0, 0, 0.0);
    }

    public PlayerStats afterGame(int score, boolean victory) {
        int games = gamesPlayed + 1;
        double average = (averageScore * gamesPlayed + score) / games;
        return new PlayerStats(games, victories + (victory ? 1 : 0), average);
    }
}
