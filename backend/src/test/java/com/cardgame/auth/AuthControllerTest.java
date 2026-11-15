package com.cardgame.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardgame.config.SecurityConfig;
import com.cardgame.player.Gender;
import com.cardgame.player.PlayerService;
import com.cardgame.player.UsernameTakenException;
import com.cardgame.player.dto.PlayerDto;
import com.cardgame.realtime.RealtimeSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthService.class})
class AuthControllerTest {

    private static final PlayerDto ALICE = new PlayerDto(1L, "alice", 25, Gender.FEMALE);

    @Autowired
    MockMvc mvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    PlayerService playerService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    RealtimeSessionRegistry realtimeSessions;

    // ---- register ----

    @Test
    void registerCreatesTheAccountWithAHashedPasswordAndReturns201() throws Exception {
        given(playerService.create(any())).willReturn(ALICE);

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"password123","age":25,"gender":"FEMALE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(playerService).create(argThat(p -> p.username().equals("alice")
                && passwordEncoder.matches("password123", p.passwordHash())
                && p.passwordHash().startsWith("{bcrypt}")));
    }

    @Test
    void registerAcceptsAMissingGender() throws Exception {
        given(playerService.create(any())).willReturn(new PlayerDto(2L, "bob", 30, null));

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"password123","age":30}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gender").isEmpty());
    }

    @Test
    void registerWithInvalidFieldsReturns400ProblemWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a b","password":"short","age":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'age')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'age')].message").value("L'âge doit être au moins 1."));

        verify(playerService, never()).create(any());
    }

    // G1: D18 username rules and D23 password lengths at HTTP level
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} -> 400 on {1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "{\"username\":\"ab\",\"password\":\"password123\",\"age\":25}                       | username",
            "{\"username\":\"abcdefghijklmnopqrstu\",\"password\":\"password123\",\"age\":25}    | username",
            "{\"username\":\"bad name\",\"password\":\"password123\",\"age\":25}                 | username",
            "{\"username\":\"bad!\",\"password\":\"password123\",\"age\":25}                     | username",
            "{\"username\":\"alice\",\"password\":\"1234567\",\"age\":25}                        | password",
            "{\"username\":\"alice\",\"password\":\"\",\"age\":25}                               | password",
            "{\"username\":\"alice\",\"password\":\"password123\",\"age\":0}                     | age",
            "{\"username\":\"alice\",\"password\":\"password123\"}                                 | age",
    })
    void registerRejectsInvalidFields(String body, String field) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"))
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());

        verify(playerService, never()).create(any());
    }

    @Test
    void registerAcceptsPasswordsOfEightAndSeventyTwoCharactersAndRejectsSeventyThree() throws Exception {
        given(playerService.create(any())).willReturn(ALICE);

        for (int length : new int[] {8, 72}) {
            mvc.perform(post("/api/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"%s\",\"age\":25}".formatted("p".repeat(length))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"%s\",\"age\":25}".formatted("p".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("Le mot de passe doit contenir entre 8 et 72 caractères."));
    }

    @Test
    void registerAcceptsUsernamesOfThreeAndTwentyCharacters() throws Exception {
        given(playerService.create(any())).willReturn(ALICE);

        for (String name : new String[] {"a_b", "A-b_c-d_e-f_g-h_i-j_"}) {
            mvc.perform(post("/api/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"password123\",\"age\":1}".formatted(name)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void registerWithAnUnknownGenderReturns400() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"password123","age":25,"gender":"Homme"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void registerWithATakenUsernameReturns409Problem() throws Exception {
        given(playerService.create(any())).willThrow(new UsernameTakenException("Alice"));

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"Alice","password":"password123","age":25}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nom d'utilisateur déjà pris"));
    }

    @Test
    void registerWithoutCsrfTokenReturns403Problem() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"password123","age":25}"""))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Jeton CSRF invalide"));

        verify(playerService, never()).create(any());
    }

    // ---- login ----

    @Test
    void loginWithValidCredentialsStoresTheSecurityContextInTheSession() throws Exception {
        givenAccount("alice", "password123");
        given(playerService.getByUsername("alice")).willReturn(ALICE);

        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    // G2 (D18): the principal and the response use the stored spelling, whatever case was typed
    @Test
    void loginIgnoresCaseAndReturnsTheStoredSpelling() throws Exception {
        given(userDetailsService.loadUserByUsername("ALICE")).willReturn(
                User.withUsername("alice").password(passwordEncoder.encode("password123")).roles("PLAYER").build());
        given(playerService.getByUsername("alice")).willReturn(ALICE);

        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ALICE","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andReturn();

        Object context = result.getRequest().getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(((org.springframework.security.core.context.SecurityContext) context).getAuthentication().getName())
                .isEqualTo("alice");
    }

    @Test
    void loginWithAWrongPasswordReturns401Problem() throws Exception {
        givenAccount("alice", "password123");

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Échec de la connexion"))
                .andExpect(jsonPath("$.detail").value("Nom d'utilisateur ou mot de passe invalide."));
    }

    @Test
    void loginWithAnUnknownUserReturnsTheSame401Problem() throws Exception {
        given(userDetailsService.loadUserByUsername("ghost")).willThrow(new UsernameNotFoundException("x"));

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ghost","password":"password123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Nom d'utilisateur ou mot de passe invalide."));
    }

    @Test
    void loginWithBlankFieldsReturns400() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    // CSRF cookie behaviour (first GET sets it, rotation on login) is asserted over real HTTP in
    // AuthFlowIntegrationTest: Spring Security Test's csrf() swaps the filter's repository in this slice.

    // ---- me ----

    @Test
    void meWithoutSessionReturns401Problem() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }


    @Test
    void meReturnsTheLoggedInPlayer() throws Exception {
        given(playerService.getByUsername("alice")).willReturn(ALICE);

        mvc.perform(get("/api/auth/me").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.age").value(25));
    }

    // ---- logout ----

    @Test
    void logoutInvalidatesTheSessionAndReturns204() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/logout").with(csrf()).with(user("alice")).session(session))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        verify(realtimeSessions).closeAll(session.getId());
    }

    // The generated client sends this Accept when the only documented body is the error: 204 must not be a 406.
    @Test
    void logoutIgnoresAnAcceptHeaderAskingForProblemJson() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()).with(user("alice"))
                        .header("Accept", "application/problem+json"))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutCsrfStillAnswersAProblemWhenProblemJsonIsAccepted() throws Exception {
        mvc.perform(post("/api/auth/logout").with(user("alice")).header("Accept", "application/problem+json"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/logout").with(user("alice")))
                .andExpect(status().isForbidden());
    }

    private void givenAccount(String username, String rawPassword) {
        given(userDetailsService.loadUserByUsername(username)).willReturn(
                User.withUsername(username).password(passwordEncoder.encode(rawPassword)).roles("PLAYER").build());
    }
}
