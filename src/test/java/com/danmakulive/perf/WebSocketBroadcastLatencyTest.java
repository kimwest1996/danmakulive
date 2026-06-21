package com.danmakulive.perf;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

/**
 * WebSocket broadcast latency measurement.
 *
 * N subscribers connect to /ws-raw and subscribe to /topic/room/{roomId}.
 * 1 sender sends M messages via the REST API (POST /api/v1/rooms/{roomId}/danmaku).
 * Each subscriber measures the time delta between the send timestamp
 * (embedded in the content field) and the receive time.
 *
 * Usage:
 *   java com.danmakulive.perf.WebSocketBroadcastLatencyTest <subscribers> <messages> <roomId> [token]
 *
 * Prerequisites: App running on localhost:8080, room created, auth token available.
 */
public class WebSocketBroadcastLatencyTest {

    private static final String WS_URL = "ws://localhost:8080/ws-raw";
    private static final int CONNECT_TIMEOUT_SEC = 15;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java ... WebSocketBroadcastLatencyTest <subscribers> <messages> <roomId> [token]");
            System.out.println("  subscribers: number of WebSocket subscribers");
            System.out.println("  messages: number of danmaku messages to send");
            System.out.println("  roomId: target room ID");
            System.out.println("  token: optional auth token for REST API");
            return;
        }

        int subscriberCount = Integer.parseInt(args[0]);
        int messageCount = Integer.parseInt(args[1]);
        String roomId = args[2];
        String token = args.length > 3 ? args[3] : null;

        run(subscriberCount, messageCount, roomId, token);
    }

    static void run(int subscriberCount, int messageCount, String roomId, String token) throws Exception {
        System.out.println("=== WebSocket Broadcast Latency Test ===");
        System.out.println("Subscribers: " + subscriberCount + ", Messages: " + messageCount);
        System.out.println("Room: " + roomId);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch subscribersReady = new CountDownLatch(subscriberCount);
        CountDownLatch allReceived = new CountDownLatch(subscriberCount * messageCount);

        // Start subscribers
        List<StompSession> subscriberSessions = new ArrayList<>();
        for (int i = 0; i < subscriberCount; i++) {
            StompSession session = connectStompClient();
            subscriberSessions.add(session);

            session.subscribe("/topic/room/" + roomId, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    long receiveTime = System.currentTimeMillis();
                    String body = (String) payload;
                    try {
                        String content = extractJsonField(body, "content");
                        if (content != null && content.startsWith("perf-")) {
                            long sendTime = Long.parseLong(content.substring(5));
                            latencies.add(receiveTime - sendTime);
                        }
                    } catch (Exception ignored) {
                    }
                    allReceived.countDown();
                }
            });
            subscribersReady.countDown();
        }

        System.out.println("All " + subscriberCount + " subscribers connected and subscribed.");
        Thread.sleep(500);

        // Send messages via REST API
        System.out.println("Sending " + messageCount + " messages via REST API...");
        long sendStart = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            long sendTime = System.currentTimeMillis();
            String content = "perf-" + sendTime;
            sendDanmakuViaRest(roomId, content, token);
            if (i % 10 == 0 && i > 0) {
                Thread.sleep(100);
            }
        }
        long sendEnd = System.currentTimeMillis();

        // Wait for delivery
        boolean done = allReceived.await(30, TimeUnit.SECONDS);
        if (!done) {
            long received = (long) subscriberCount * messageCount - allReceived.getCount();
            System.out.println("WARNING: " + received + " / " + (subscriberCount * messageCount) + " received");
        }

        // Disconnect
        for (StompSession s : subscriberSessions) s.disconnect();

        if (latencies.isEmpty()) {
            System.out.println("ERROR: No latency samples collected. Check if broadcast is working.");
            return;
        }

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int n = sorted.size();

        System.out.println("\n=== Results ===");
        System.out.println("Samples: " + n);
        System.out.println("Send duration: " + (sendEnd - sendStart) + " ms");
        System.out.println("P50:  " + sorted.get(n / 2) + " ms");
        System.out.println("P90:  " + sorted.get((int) (n * 0.90)) + " ms");
        System.out.println("P95:  " + sorted.get((int) (n * 0.95)) + " ms");
        System.out.println("P99:  " + sorted.get((int) (n * 0.99)) + " ms");
        System.out.println("Min:  " + sorted.get(0) + " ms");
        System.out.println("Max:  " + sorted.get(n - 1) + " ms");
        System.out.println("Avg:  " + (latencies.stream().mapToLong(Long::longValue).sum() / n) + " ms");
    }

    // ==================== helpers ====================

    private static StompSession connectStompClient() throws Exception {
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new StringMessageConverter());

        return stompClient
                .connectAsync(WS_URL, new StompSessionHandlerAdapter() {})
                .get(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private static void sendDanmakuViaRest(String roomId, String content, String token) {
        try {
            String json = "{\"content\":\"" + content + "\"}";
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:8080/api/v1/rooms/" + roomId + "/danmaku?bypassRateLimit=true"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json));
            if (token != null) {
                builder.header("Authorization", token);
            }
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("Failed to send danmaku: " + e.getMessage());
        }
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }
}
