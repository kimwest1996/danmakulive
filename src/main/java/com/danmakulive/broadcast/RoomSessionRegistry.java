package com.danmakulive.broadcast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RoomSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RoomSessionRegistry.class);
    private static final int MAX_CONNECTIONS = 10_000;

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<String>>
            roomSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>
            sessionRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder>
            roomLocalCount = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    public boolean register(String roomId, String sessionId) {
        if (totalConnections.get() >= MAX_CONNECTIONS) {
            return false;
        }
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        sessionRoom.put(sessionId, roomId);
        roomLocalCount.computeIfAbsent(roomId, k -> new LongAdder()).increment();
        totalConnections.incrementAndGet();
        return true;
    }

    public void remove(String sessionId) {
        String roomId = sessionRoom.remove(sessionId);
        if (roomId != null) {
            Set<String> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }
            LongAdder counter = roomLocalCount.get(roomId);
            if (counter != null) {
                counter.decrement();
            }
        }
        totalConnections.decrementAndGet();
    }

    public Set<String> getRoomSessions(String roomId) {
        Set<String> s = roomSessions.get(roomId);
        return s != null ? s : Set.of();
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }

    public Map<String, Long> getRoomLocalCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        roomLocalCount.forEach((k, v) -> result.put(k, v.longValue()));
        return result;
    }
}
