package com.danmakulive.broadcast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Objects;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final RoomSessionRegistry registry;

    public WebSocketEventListener(RoomSessionRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        if (destination == null) return;
        // Extract roomId from "/topic/room/123" or "/topic/video/456"
        String[] parts = destination.split("/");
        if (parts.length >= 4) {
            String type = parts[2];   // "room" or "video"
            String id = parts[3];     // actual ID
            String roomId = type + ":" + id;
            if (!registry.register(roomId, sessionId)) {
                log.warn("Connection limit reached ({}/{}), rejecting session {}",
                        registry.getTotalConnections(), 10_000, sessionId);
                // 拒绝订阅：STOMP 协议层面较难优雅拒绝订阅请求
                // 连接已在 HTTP 握手层被 WebSocketConfig 拦截，这里做二次兜底
            }
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        registry.remove(sessionId);
    }
}
