package com.cardgame.player;

import com.cardgame.player.dto.NewPlayer;
import com.cardgame.player.dto.PlayerCredentials;
import com.cardgame.game.engine.PlayerStats;
import com.cardgame.player.dto.PlayerDto;
import com.cardgame.player.dto.PlayerProfileDto;
import com.cardgame.player.dto.UpdateProfileRequest;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository players;
    private final PlayerMapper mapper;

    public PlayerService(PlayerRepository players, PlayerMapper mapper) {
        this.players = players;
        this.mapper = mapper;
    }

    /**
     * @throws UsernameTakenException if the username exists, ignoring case (D18)
     */
    @Transactional
    public PlayerDto create(NewPlayer newPlayer) {
        if (players.existsByUsernameIgnoreCase(newPlayer.username())) {
            throw new UsernameTakenException(newPlayer.username());
        }
        try {
            Player saved = players.saveAndFlush(
                    new Player(newPlayer.username(), newPlayer.passwordHash(), newPlayer.age(), newPlayer.gender()));
            return mapper.toDto(saved);
        } catch (DataIntegrityViolationException e) {
            // concurrent registration of the same name: the unique index wins
            throw new UsernameTakenException(newPlayer.username());
        }
    }

    /**
     * @throws PlayerNotFoundException if there is no such player
     */
    @Transactional(readOnly = true)
    public PlayerDto getByUsername(String username) {
        return players.findByUsernameIgnoreCase(username)
                .map(mapper::toDto)
                .orElseThrow(() -> new PlayerNotFoundException(username));
    }

    @Transactional(readOnly = true)
    public Optional<PlayerCredentials> findCredentials(String username) {
        return players.findByUsernameIgnoreCase(username).map(mapper::toCredentials);
    }

    @Transactional(readOnly = true)
    public PlayerProfileDto getProfile(String username) {
        return mapper.toProfile(load(username));
    }

    /** Row 6: changes age and gender only; the stats are read fresh from the database (fixes B21). */
    @Transactional
    public PlayerProfileDto updateProfile(String username, UpdateProfileRequest request) {
        Player player = load(username);
        player.setAge(request.age());
        player.setGender(request.gender());
        return mapper.toProfile(player);
    }

    /**
     * Applies one finished game to the player's stats ({@link PlayerStats#afterGame}, D6). Joins the caller's
     * transaction.
     *
     * @return the player id
     */
    @Transactional
    public long recordGameResult(String username, int score, boolean victory) {
        Player player = load(username);
        PlayerStats stats = new PlayerStats(player.getGamesPlayed(), player.getVictories(), player.getAverageScore())
                .afterGame(score, victory);
        player.setStats(stats.gamesPlayed(), stats.victories(), stats.averageScore());
        return player.getId();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> usernamesByIds(Collection<Long> ids) {
        return players.findAllById(ids).stream()
                .collect(Collectors.toMap(Player::getId, Player::getUsername, (a, b) -> a));
    }

    private Player load(String username) {
        return players.findByUsernameIgnoreCase(username).orElseThrow(() -> new PlayerNotFoundException(username));
    }
}
