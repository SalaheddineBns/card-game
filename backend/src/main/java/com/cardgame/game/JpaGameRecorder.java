package com.cardgame.game;

import com.cardgame.game.engine.GameEngine;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.player.PlayerService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaGameRecorder implements GameRecorder {

    private final GameRepository games;
    private final ParticipationRepository participations;
    private final PlayerService playerService;

    JpaGameRecorder(GameRepository games, ParticipationRepository participations, PlayerService playerService) {
        this.games = games;
        this.participations = participations;
        this.playerService = playerService;
    }

    @Override
    @Transactional
    public long createGame(GameSettings settings, Instant startedAt) {
        Game game = games.save(new Game(startedAt, settings.totalRounds(), (int) settings.roundDuration().toSeconds()));
        return game.getId();
    }

    @Override
    @Transactional
    public void finishGame(long gameId, Instant finishedAt, List<PlayerGameRecord> records) {
        Game game = games.findById(gameId)
                .orElseThrow(() -> new IllegalStateException("Unknown game " + gameId));
        game.finish(finishedAt);
        for (PlayerGameRecord record : records) {
            long playerId = playerService.recordGameResult(record.username(), record.score(), record.victory());
            participations.save(new Participation(game, playerId, record.score()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FinishedGame> findFinished(long gameId) {
        return games.findById(gameId)
                .filter(game -> game.getStatus() == GameStatus.FINISHED)
                .map(game -> {
                    List<Participation> rows = participations.findByGameIdOrderByIdAsc(gameId);
                    Map<Long, String> names = playerService.usernamesByIds(
                            rows.stream().map(Participation::getPlayerId).toList());
                    return new FinishedGame(gameId, game.getFinishedAt(), toRecords(rows, names));
                });
    }

    /** Victory is recomputed from the scores with the result rule, so it always matches it (D3-D5). */
    private static List<PlayerGameRecord> toRecords(List<Participation> rows, Map<Long, String> names) {
        SequencedMap<String, Integer> scores = new LinkedHashMap<>();
        rows.forEach(p -> scores.put(names.get(p.getPlayerId()), p.getScore()));
        String winner = GameEngine.determineResult(scores).winner();
        return scores.entrySet().stream()
                .map(e -> new PlayerGameRecord(e.getKey(), e.getValue(), e.getKey().equals(winner)))
                .toList();
    }
}
