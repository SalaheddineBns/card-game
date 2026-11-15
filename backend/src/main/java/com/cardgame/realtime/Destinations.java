package com.cardgame.realtime;

/** STOMP destinations (see docs/realtime-protocol.md). User queues are subscribed as {@code /user/queue/...}. */
public final class Destinations {

    public static final String PRESENCE_TOPIC = "/topic/presence";
    public static final String INVITATIONS_QUEUE = "/queue/invitations";
    public static final String GAMES_QUEUE = "/queue/games";
    public static final String ERRORS_QUEUE = "/queue/errors";
    public static final String GAME_TOPIC_PREFIX = "/topic/game/";

    private Destinations() {
    }

    public static String gameTopic(long gameId) {
        return GAME_TOPIC_PREFIX + gameId;
    }
}
