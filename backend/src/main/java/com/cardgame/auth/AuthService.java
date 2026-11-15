package com.cardgame.auth;

import com.cardgame.auth.dto.LoginRequest;
import com.cardgame.auth.dto.RegisterRequest;
import com.cardgame.player.PlayerService;
import com.cardgame.player.dto.NewPlayer;
import com.cardgame.player.dto.PlayerDto;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final PlayerService playerService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(PlayerService playerService, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.playerService = playerService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Creates an account with a bcrypt-hashed password. Does not log the player in.
     *
     * @throws com.cardgame.player.UsernameTakenException if the username exists (ignoring case)
     */
    public PlayerDto register(RegisterRequest request) {
        return playerService.create(new NewPlayer(
                request.username(), passwordEncoder.encode(request.password()), request.age(), request.gender()));
    }

    /**
     * Checks the credentials.
     *
     * @throws AuthenticationException (bad credentials) when they do not match
     */
    public Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
    }

    public PlayerDto currentPlayer(String username) {
        return playerService.getByUsername(username);
    }
}
