package com.cardgame.presence;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardgame.WebMvcSecurityTestSupport;
import com.cardgame.presence.dto.OnlinePlayerDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PresenceController.class)
@WebMvcSecurityTestSupport
class PresenceControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PresenceService presence;

    @Test
    void listsOnlinePlayersWithoutTheCaller() throws Exception {
        given(presence.onlinePlayers("alice")).willReturn(List.of(
                new OnlinePlayerDto("bob", PlayerStatus.IN_GAME), new OnlinePlayerDto("carol", PlayerStatus.ONLINE)));

        mvc.perform(get("/api/players/online").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("bob"))
                .andExpect(jsonPath("$[0].status").value("IN_GAME"));
    }

    @Test
    void requiresASession() throws Exception {
        mvc.perform(get("/api/players/online")).andExpect(status().isUnauthorized());
    }
}
