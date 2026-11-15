package com.cardgame.game;

import com.cardgame.game.engine.GameSettings;
import java.util.List;

/** Starts a game; implemented by {@link GameService}. The invitation feature depends only on this. */
public interface GameLauncher {

    /** @return the new game id */
    long startGame(List<String> players, GameSettings settings);
}
