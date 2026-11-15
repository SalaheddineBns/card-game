package com.cardgame.player;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    /** Case-insensitive lookup that matches the {@code uq_players_username_lower} index (D18). */
    @Query("select p from Player p where lower(p.username) = lower(:username)")
    Optional<Player> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(p) > 0 from Player p where lower(p.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);
}
