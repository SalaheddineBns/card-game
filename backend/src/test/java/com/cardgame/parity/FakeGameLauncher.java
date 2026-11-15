package com.cardgame.parity;

import com.cardgame.game.GameLauncher;
import com.cardgame.game.engine.GameSettings;
import java.util.ArrayList;
import java.util.List;

/** Records game starts requested by the invitation service. Game ids start at 100. */
final class FakeGameLauncher implements GameLauncher {

    record Launch(long gameId, List<String> players, GameSettings settings) {
    }

    private final List<Launch> launches = new ArrayList<>();
    private long nextId = 100;

    @Override
    public long startGame(List<String> players, GameSettings settings) {
        Launch launch = new Launch(nextId++, List.copyOf(players), settings);
        launches.add(launch);
        return launch.gameId();
    }

    List<Launch> launches() {
        return List.copyOf(launches);
    }
}
