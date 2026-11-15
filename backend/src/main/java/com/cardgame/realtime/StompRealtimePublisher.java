package com.cardgame.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompRealtimePublisher implements RealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void toUser(String username, String destination, RealtimeEvent event) {
        messagingTemplate.convertAndSendToUser(username, destination, event);
    }

    @Override
    public void toTopic(String destination, RealtimeEvent event) {
        messagingTemplate.convertAndSend(destination, event);
    }
}
