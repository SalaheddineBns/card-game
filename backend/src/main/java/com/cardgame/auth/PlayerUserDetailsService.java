package com.cardgame.auth;

import com.cardgame.player.PlayerService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads players for Spring Security. The principal name is the username as stored, so "ALICE" logs in
 * as "alice" (D18).
 */
@Service
public class PlayerUserDetailsService implements UserDetailsService {

    static final String ROLE_PLAYER = "PLAYER";

    private final PlayerService playerService;

    public PlayerUserDetailsService(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return playerService.findCredentials(username)
                .map(c -> User.withUsername(c.username()).password(c.passwordHash()).roles(ROLE_PLAYER).build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
