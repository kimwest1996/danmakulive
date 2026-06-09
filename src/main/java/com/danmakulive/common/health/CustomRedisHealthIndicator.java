package com.danmakulive.common.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class CustomRedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public CustomRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection conn = connectionFactory.getConnection()) {
            String pong = conn.ping();
            if (!"PONG".equals(pong)) {
                return Health.down().withDetail("reason", "PING returned " + pong).build();
            }

            Properties memory = conn.serverCommands().info("memory");
            long used = Long.parseLong(memory.getProperty("used_memory"));
            String maxmemoryStr = memory.getProperty("maxmemory");
            long max = Long.parseLong(maxmemoryStr != null ? maxmemoryStr : "0");

            Health.Builder builder = Health.up()
                    .withDetail("used_memory_human", memory.getProperty("used_memory_human"))
                    .withDetail("used_memory_peak_human", memory.getProperty("used_memory_peak_human"));

            if (max > 0) {
                double ratio = (double) used / max;
                builder.withDetail("maxmemory_human", memory.getProperty("maxmemory_human"))
                       .withDetail("memory_usage_pct", String.format("%.1f%%", ratio * 100));
                if (ratio >= 0.8) {
                    builder.status("WARN")
                           .withDetail("warning", "Memory usage exceeds 80%");
                }
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", e.getMessage()).build();
        }
    }
}
