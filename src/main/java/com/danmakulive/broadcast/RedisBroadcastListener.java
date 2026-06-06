package com.danmakulive.broadcast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisBroadcastListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RedisBroadcastListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        // channel = "room:123:pubsub" → destination = "/topic/room/123"
        String roomId = channel.replace("room:", "").replace(":pubsub", "");
        messagingTemplate.convertAndSend("/topic/room/" + roomId, body);
    }
}
