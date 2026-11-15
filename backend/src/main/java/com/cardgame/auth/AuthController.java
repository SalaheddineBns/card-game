package com.cardgame.auth;

import com.cardgame.auth.dto.LoginRequest;
import com.cardgame.auth.dto.RegisterRequest;
import com.cardgame.config.SecurityConfig;
import com.cardgame.player.dto.PlayerDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth", description = "Registration and session login/logout")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler sessionCookieClearing =
            new CookieClearingLogoutHandler(SecurityConfig.SESSION_COOKIE_NAME);
    private final CsrfLogoutHandler csrfLogoutHandler;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthService authService, SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy,
                          CsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfLogoutHandler = new CsrfLogoutHandler(csrfTokenRepository);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(operationId = "register", summary = "Create an account", description = "Does not log in. Requires the X-XSRF-TOKEN header.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed (field errors in `errors`)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Username already taken (ignoring case)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public PlayerDto register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(operationId = "login", summary = "Log in", description = """
            Starts a session: sets the JSESSIONID cookie (HttpOnly) and rotates the XSRF-TOKEN cookie. \
            Requires the X-XSRF-TOKEN header.""")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Invalid username or password",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public PlayerDto login(@Valid @RequestBody LoginRequest request,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authService.authenticate(request);
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return authService.currentPlayer(authentication.getName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(operationId = "logout", summary = "Log out", description = """
            Invalidates the session and clears the JSESSIONID and XSRF-TOKEN cookies. Idempotent: also 204 \
            without a session. Requires the X-XSRF-TOKEN header.""")
    @ApiResponse(responseCode = "204", description = "Logged out")
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        csrfLogoutHandler.logout(request, response, authentication);
        sessionCookieClearing.logout(request, response, authentication);
        securityContextLogoutHandler.logout(request, response, authentication);
    }

    @GetMapping("/me")
    @Operation(operationId = "me", summary = "Current player", description = "The player of the current session.")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "401", description = "No valid session",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public PlayerDto me(@AuthenticationPrincipal UserDetails principal) {
        return authService.currentPlayer(principal.getUsername());
    }
}
