package com.cardgame.game;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardgame.WebMvcSecurityTestSupport;
import com.cardgame.game.dto.CardDto;
import com.cardgame.game.dto.DealDto;
import com.cardgame.game.dto.GameResultDto;
import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.game.dto.GameSnapshotDto;
import com.cardgame.game.dto.PlayerCardDto;
import com.cardgame.game.dto.PlayerScoreDto;
import com.cardgame.game.engine.GamePhase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GameController.class)
@WebMvcSecurityTestSupport
class GameControllerTest {

    private static final Instant ENDS = Instant.parse("2026-01-01T10:00:10Z");
    private static final GameSnapshotDto SNAPSHOT = new GameSnapshotDto(7L, List.of("alice", "bob"),
            new GameSettingsDto(3, 10), GamePhase.IN_PROGRESS, 1, ENDS,
            List.of(new PlayerScoreDto("alice", 0), new PlayerScoreDto("bob", 0)),
            new DealDto(1, List.of(new PlayerCardDto("alice", "A", "red"), new PlayerCardDto("bob", "L", "green")),
                    new CardDto("L", "red")));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GameService games;

    @Test
    void currentGameIsReturnedWhenThereIsOne() throws Exception {
        given(games.currentGameOf("alice")).willReturn(Optional.of(SNAPSHOT));

        mvc.perform(get("/api/games/current").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(7))
                .andExpect(jsonPath("$.roundEndsAt").value("2026-01-01T10:00:10Z"))
                .andExpect(jsonPath("$.deal.centerCard.color").value("red"))
                .andExpect(jsonPath("$.deal.playerCards[1].symbol").value("L"));
    }

    @Test
    void currentGameIs204WhenThereIsNone() throws Exception {
        given(games.currentGameOf("carol")).willReturn(Optional.empty());

        mvc.perform(get("/api/games/current").with(user("carol")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void snapshotErrorsAreProblems() throws Exception {
        given(games.snapshot(7L, "carol")).willThrow(new NotAGamePlayerException(7L));
        given(games.snapshot(8L, "alice")).willThrow(new GameFinishedException(8L));
        given(games.snapshot(9L, "alice")).willThrow(new GameNotFoundException(9L));

        mvc.perform(get("/api/games/7").with(user("carol"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Accès refusé"));
        mvc.perform(get("/api/games/8").with(user("alice"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Partie terminée"));
        mvc.perform(get("/api/games/9").with(user("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void snapshotOfALiveGame() throws Exception {
        given(games.snapshot(7L, "bob")).willReturn(SNAPSHOT);

        mvc.perform(get("/api/games/7").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.settings.roundSeconds").value(10));
    }

    @Test
    void resultOfAFinishedGame() throws Exception {
        given(games.result(7L, "alice")).willReturn(new GameResultDto(7L, ENDS, true, null, null,
                List.of("alice", "bob"), 0, List.of(), 0,
                List.of(new PlayerScoreDto("alice", 0), new PlayerScoreDto("bob", 0))));
        given(games.result(8L, "alice")).willThrow(new GameInProgressException(8L));

        mvc.perform(get("/api/games/7/result").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draw").value(true))
                .andExpect(jsonPath("$.winner").isEmpty())
                .andExpect(jsonPath("$.trailers.length()").value(0));
        mvc.perform(get("/api/games/8/result").with(user("alice")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Partie en cours"));
    }

    @Test
    void abandonedGameIsA410Problem() throws Exception {
        given(games.snapshot(10L, "alice")).willThrow(new GameAbandonedException());
        given(games.result(10L, "alice")).willThrow(new GameAbandonedException());

        mvc.perform(get("/api/games/10").with(user("alice")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.title").value("Partie interrompue"))
                .andExpect(jsonPath("$.detail").value("Cette partie a été interrompue par un redémarrage du serveur."));
        mvc.perform(get("/api/games/10/result").with(user("alice"))).andExpect(status().isGone());
    }

    @Test
    void requiresASession() throws Exception {
        mvc.perform(get("/api/games/current")).andExpect(status().isUnauthorized());
    }
}
