package com.cardgame.invitation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardgame.WebMvcSecurityTestSupport;
import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.invitation.dto.InvitationDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvitationController.class)
@WebMvcSecurityTestSupport
class InvitationControllerTest {

    private static final Instant EXPIRES = Instant.parse("2026-01-01T10:01:00Z");
    private static final InvitationDto PENDING = new InvitationDto(1L, "alice", List.of("bob"), List.of(),
            new GameSettingsDto(3, 10), InvitationStatus.PENDING, null, EXPIRES, null);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InvitationService invitations;

    @Test
    void createsAnInvitationForTheCaller() throws Exception {
        given(invitations.create(eq("alice"), any())).willReturn(PENDING);

        mvc.perform(post("/api/invitations").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":["bob"],"settings":{"totalRounds":3,"roundSeconds":10}}"""))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").value("2026-01-01T10:01:00Z"))
                .andExpect(jsonPath("$.gameId").isEmpty());

        verify(invitations).create("alice",
                new CreateInvitationRequest(List.of("bob"), new GameSettingsDto(3, 10)));
    }

    @Test
    void rejectsSettingsOutsideTheLimits() throws Exception {
        mvc.perform(post("/api/invitations").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":["bob"],"settings":{"totalRounds":21,"roundSeconds":4}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'settings.totalRounds')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'settings.roundSeconds')]").exists());

        verify(invitations, never()).create(any(), any());
    }

    @Test
    void rejectsAnEmptyInviteeList() throws Exception {
        mvc.perform(post("/api/invitations").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Invitez au moins un joueur."));
    }

    @Test
    void anUnavailableInviteeIsA409Problem() throws Exception {
        given(invitations.create(eq("alice"), any()))
                .willThrow(new PlayerUnavailableException("Le joueur « bob » n'est pas connecté."));

        mvc.perform(post("/api/invitations").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":["bob"]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Joueur indisponible"))
                .andExpect(jsonPath("$.detail").value("Le joueur « bob » n'est pas connecté."));
    }

    @Test
    void acceptAndDeclineActAsTheCaller() throws Exception {
        given(invitations.accept(1L, "bob")).willReturn(PENDING);
        given(invitations.decline(1L, "bob")).willThrow(new InvitationClosedException(1L, InvitationStatus.ACCEPTED));

        mvc.perform(post("/api/invitations/1/accept").with(user("bob")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(post("/api/invitations/1/decline").with(user("bob")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invitation fermée"));
    }

    @Test
    void answeringSomeoneElsesInvitationIsNotFound() throws Exception {
        given(invitations.accept(1L, "mallory")).willThrow(new InvitationNotFoundException(1L));

        mvc.perform(post("/api/invitations/1/accept").with(user("mallory")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptRequiresCsrf() throws Exception {
        mvc.perform(post("/api/invitations/1/accept").with(user("bob"))).andExpect(status().isForbidden());
    }

    @Test
    void listsPendingInvitations() throws Exception {
        given(invitations.pendingFor("bob")).willReturn(List.of(PENDING));

        mvc.perform(get("/api/invitations/pending").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inviter").value("alice"));
    }
}
