package com.danmakulive.broadcast;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisBroadcastListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    public RedisBroadcastListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        // channel pattern: "room:{id}:pubsub" or "video:{id}:pubsub"
        int firstColon = channel.indexOf(':');
        int lastColon = channel.lastIndexOf(':');
        if (firstColon > 0 && lastColon > firstColon) {
            String type = channel.substring(0, firstColon);       // "room" or "video"
            String id = channel.substring(firstColon + 1, lastColon);  // the actual ID
            messagingTemplate.convertAndSend("/topic/" + type + "/" + id, body);
        }
    }
}
