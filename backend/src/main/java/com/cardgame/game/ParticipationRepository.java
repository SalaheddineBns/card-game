package com.cardgame.game;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipationRepository extends JpaRepository<Participation, Long> {

    /** Participations of a game in insertion (= player) order. */
    List<Participation> findByGameIdOrderByIdAsc(Long gameId);
}
