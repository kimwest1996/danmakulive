package com.danmakulive.perf;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket connection capacity test.
 *
 * Gradually ramps up concurrent WebSocket connections to find the practical
 * limit and measure per-connection memory overhead.
 *
 * Usage:
 *   java com.danmakulive.perf.WebSocketCapacityTest <roomId>
 *
 * Prerequisites: App running on localhost:8080.
 */
public class WebSocketCapacityTest {

    private static final String WS_URL = "ws://localhost:8080/ws-raw";
    private static final int CONNECT_TIMEOUT_SEC = 15;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ... WebSocketCapacityTest <roomId>");
            return;
        }
        String roomId = args[0];
        run(roomId);
    }

    static void run(String roomId) {
        System.out.println("=== WebSocket Connection Capacity Test ===");
        System.out.println("Room: " + roomId);

        int[] steps = {50, 100, 200, 500, 1000, 2000, 3000, 5000};

        for (int target : steps) {
            System.out.print("\nTarget: " + target + "  ... ");
            List<StompSession> sessions = new ArrayList<>();
            int success = 0;
            int failed = 0;

            long beforeMem = usedMemoryMB();

            for (int i = 0; i < target; i++) {
                try {
                    StompSession session = connectStompClient();
                    session.subscribe("/topic/room/" + roomId, new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) { return String.class; }
                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {}
                    });
                    sessions.add(session);
                    success++;
                } catch (Exception e) {
                    failed++;
                    break;
                }
            }

            long afterMem = usedMemoryMB();
            long perConnectionKb = success > 0 ? (afterMem - beforeMem) * 1024 / success : 0;

            System.out.printf("Success: %d, Failed: %d, Heap: %d → %d MB (~%d KB/conn)%n",
                    success, failed, beforeMem, afterMem, perConnectionKb);

            // Disconnect all
            for (StompSession s : sessions) {
                try { s.disconnect(); } catch (Exception ignored) {}
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
    }

    private static StompSession connectStompClient() throws Exception {
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new StringMessageConverter());

        return stompClient
                .connectAsync(WS_URL, new StompSessionHandlerAdapter() {})
                .get(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private static long usedMemoryMB() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }
}
