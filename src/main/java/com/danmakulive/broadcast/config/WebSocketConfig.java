package com.danmakulive.broadcast.config;

import com.danmakulive.broadcast.RoomSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final int MAX_CONNECTIONS = 10_000;

    private final RoomSessionRegistry sessionRegistry;

    public WebSocketConfig(RoomSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(connectionLimitInterceptor());

        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*")
                .addInterceptors(connectionLimitInterceptor());
    }

    private HandshakeInterceptor connectionLimitInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(
                    org.springframework.http.server.ServerHttpRequest request,
                    org.springframework.http.server.ServerHttpResponse response,
                    org.springframework.web.socket.WebSocketHandler wsHandler,
                    Map<String, Object> attributes) {
                if (sessionRegistry.getTotalConnections() >= MAX_CONNECTIONS) {
                    response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    log.warn("WebSocket connection rejected: {}/{} connections", sessionRegistry.getTotalConnections(), MAX_CONNECTIONS);
                    return false;
                }
                return true;
            }

            @Override
            public void afterHandshake(
                    org.springframework.http.server.ServerHttpRequest request,
                    org.springframework.http.server.ServerHttpResponse response,
                    org.springframework.web.socket.WebSocketHandler wsHandler,
                    Exception exception) {
            }
        };
    }
}
