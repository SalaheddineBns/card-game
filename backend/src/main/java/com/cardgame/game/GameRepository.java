package com.cardgame.game;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GameRepository extends JpaRepository<Game, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Game g set g.status = com.cardgame.game.GameStatus.ABANDONED, g.finishedAt = :cutoff
            where g.status = com.cardgame.game.GameStatus.IN_PROGRESS and g.startedAt < :cutoff""")
    int abandonInProgressStartedBefore(@Param("cutoff") Instant cutoff);

    @Query("select g.status from Game g where g.id = :id")
    Optional<GameStatus> findStatusById(@Param("id") Long id);
}
