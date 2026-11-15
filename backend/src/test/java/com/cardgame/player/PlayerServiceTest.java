package com.cardgame.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cardgame.player.dto.NewPlayer;
import com.cardgame.player.dto.PlayerDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    PlayerRepository repository;

    private PlayerService service() {
        return new PlayerService(repository, new PlayerMapperImpl());
    }

    @Test
    void createsAPlayerWithZeroStats() {
        given(repository.existsByUsernameIgnoreCase("alice")).willReturn(false);
        given(repository.saveAndFlush(any(Player.class))).willAnswer(inv -> inv.getArgument(0));

        PlayerDto dto = service().create(new NewPlayer("alice", "{bcrypt}hash", 25, Gender.FEMALE));

        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.age()).isEqualTo(25);
        assertThat(dto.gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void rejectsAUsernameThatAlreadyExistsIgnoringCase() {
        given(repository.existsByUsernameIgnoreCase("ALICE")).willReturn(true);

        assertThatThrownBy(() -> service().create(new NewPlayer("ALICE", "h", 25, null)))
                .isInstanceOf(UsernameTakenException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reportsAConcurrentDuplicateAsUsernameTaken() {
        given(repository.existsByUsernameIgnoreCase("alice")).willReturn(false);
        given(repository.saveAndFlush(any(Player.class))).willThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service().create(new NewPlayer("alice", "h", 25, null)))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void unknownUsernameIsNotFound() {
        given(repository.findByUsernameIgnoreCase("ghost")).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service().getByUsername("ghost")).isInstanceOf(PlayerNotFoundException.class);
    }
}
