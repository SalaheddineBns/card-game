package com.cardgame.player;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardgame.WebMvcSecurityTestSupport;
import com.cardgame.player.dto.PlayerProfileDto;
import com.cardgame.player.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
@WebMvcSecurityTestSupport
class PlayerControllerTest {

    private static final PlayerProfileDto PROFILE = new PlayerProfileDto("alice", 25, Gender.FEMALE, 4, 2, 3.0);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlayerService playerService;

    @Test
    void returnsTheCallersProfileWithStatsButNoPassword() throws Exception {
        given(playerService.getProfile("alice")).willReturn(PROFILE);

        mvc.perform(get("/api/players/me").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.gamesPlayed").value(4))
                .andExpect(jsonPath("$.victories").value(2))
                .andExpect(jsonPath("$.averageScore").value(3.0))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void profileRequiresASession() throws Exception {
        mvc.perform(get("/api/players/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void updatesAgeAndGenderOfTheCallerOnly() throws Exception {
        given(playerService.updateProfile(eq("alice"), any())).willReturn(
                new PlayerProfileDto("alice", 26, null, 4, 2, 3.0));

        mvc.perform(put("/api/players/me").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"age":26,"gender":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(26));

        verify(playerService).updateProfile("alice", new UpdateProfileRequest(26, null));
    }

    @Test
    void rejectsAnInvalidAgeWithFieldErrors() throws Exception {
        mvc.perform(put("/api/players/me").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"age":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("age"))
                .andExpect(jsonPath("$.errors[0].message").value("L'âge doit être au moins 1."));

        verify(playerService, never()).updateProfile(any(), any());
    }

    @Test
    void updateRequiresCsrf() throws Exception {
        mvc.perform(put("/api/players/me").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"age\":26}"))
                .andExpect(status().isForbidden());
    }
}
