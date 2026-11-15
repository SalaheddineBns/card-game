package com.cardgame.parity;

import com.cardgame.realtime.RealtimeEvent;
import com.cardgame.realtime.RealtimePublisher;
import java.util.ArrayList;
import java.util.List;

/** Fake messaging port: records every event instead of sending it over STOMP. */
final class RecordingPublisher implements RealtimePublisher {

    /** {@code username} is null for topic messages. */
    record Sent(String username, String destination, RealtimeEvent event) {
    }

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void toUser(String username, String destination, RealtimeEvent event) {
        sent.add(new Sent(username, destination, event));
    }

    @Override
    public void toTopic(String destination, RealtimeEvent event) {
        sent.add(new Sent(null, destination, event));
    }

    List<Sent> all() {
        return List.copyOf(sent);
    }

    void clear() {
        sent.clear();
    }

    /** Events of {@code type} sent to {@code username} on {@code destination}. */
    <T extends RealtimeEvent> List<T> toUser(String username, String destination, Class<T> type) {
        return sent.stream()
                .filter(s -> username.equals(s.username()) && destination.equals(s.destination()))
                .map(Sent::event).filter(type::isInstance).map(type::cast).toList();
    }

    /** Events of {@code type} sent on topic {@code destination}. */
    <T extends RealtimeEvent> List<T> onTopic(String destination, Class<T> type) {
        return sent.stream()
                .filter(s -> s.username() == null && destination.equals(s.destination()))
                .map(Sent::event).filter(type::isInstance).map(type::cast).toList();
    }

    /** Usernames that received at least one event of {@code type} on {@code destination}. */
    List<String> recipientsOf(String destination, Class<? extends RealtimeEvent> type) {
        return sent.stream()
                .filter(s -> s.username() != null && destination.equals(s.destination()) && type.isInstance(s.event()))
                .map(Sent::username).distinct().toList();
    }
}
