package com.cardgame.player;

import com.cardgame.player.dto.NewPlayer;
import com.cardgame.player.dto.PlayerCredentials;
import com.cardgame.player.dto.PlayerDto;
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
}
