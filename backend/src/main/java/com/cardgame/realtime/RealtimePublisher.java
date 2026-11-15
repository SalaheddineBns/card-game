package com.cardgame.realtime;

/** Messaging port. Services publish through it; only {@link StompRealtimePublisher} knows STOMP. */
public interface RealtimePublisher {

    /** To every session of {@code username}; {@code destination} like {@code "/queue/invitations"}. */
    void toUser(String username, String destination, RealtimeEvent event);

    /** To every subscriber of {@code destination}, like {@code "/topic/presence"} or {@code "/topic/game/7"}. */
    void toTopic(String destination, RealtimeEvent event);
}
