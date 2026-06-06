package com.danmakulive.broadcast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;

class RedisBroadcastListenerTest {

    private SimpMessagingTemplate messagingTemplate;
    private RedisBroadcastListener listener;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        listener = new RedisBroadcastListener(messagingTemplate);
    }

    @Test
    void convertsChannelToDestinationAndSends() {
        Message message = mock(Message.class);
        when(message.getChannel()).thenReturn("room:room123:pubsub".getBytes(StandardCharsets.UTF_8));
        when(message.getBody()).thenReturn("{\"content\":\"test\"}".getBytes(StandardCharsets.UTF_8));

        listener.onMessage(message, null);

        verify(messagingTemplate).convertAndSend("/topic/room/room123", "{\"content\":\"test\"}");
    }
}
