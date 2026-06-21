package com.danmakulive.perf;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.danmaku.pipeline.stage.RateLimitStage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rate Limiter correctness and performance tests.
 * Requires Redis on localhost:6379 (docker-compose up).
 */
class RateLimiterPerfTest {

    private static RateLimitStage stage;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();

        StringRedisTemplate redis = new StringRedisTemplate();
        redis.setConnectionFactory(factory);
        redis.setKeySerializer(StringRedisSerializer.UTF_8);
        redis.setValueSerializer(StringRedisSerializer.UTF_8);
        redis.afterPropertiesSet();

        stage = new RateLimitStage(redis);
    }

    // ==================== correctness ====================

    @Test
    void firstFiveWithinLimitShouldPass() {
        String userId = "correctness-user-" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            PipelineContext ctx = liveContext(userId, "10.0.0.1", "room-correct");
            stage.process(ctx);
            assertFalse(ctx.hasError(), "第 " + (i + 1) + " 次应该放行");
        }
    }

    @Test
    void sixthRequestShouldBeBlocked() {
        String userId = "blocked-user-" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            PipelineContext ctx = liveContext(userId, "10.0.1." + (System.nanoTime() % 255), "room-block");
            stage.process(ctx);
        }
        PipelineContext ctx = liveContext(userId, "10.0.1.1", "room-block");
        stage.process(ctx);
        assertTrue(ctx.hasError(), "第 6 次应被拦截（窗口未过期）");
    }

    @Test
    void windowExpiresAndAllowsAgain() throws Exception {
        String userId = "expire-user-" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            PipelineContext ctx = liveContext(userId, "10.0.2." + (System.nanoTime() % 255), "room-expire");
            stage.process(ctx);
        }
        Thread.sleep(1100);
        PipelineContext ctx = liveContext(userId, "10.0.2.1", "room-expire");
        stage.process(ctx);
        assertFalse(ctx.hasError(), "窗口过期后应重新放行");
    }

    // ==================== performance ====================

    @Test
    void singleKeyLuaLatency() {
        String roomId = "perf-room-" + System.nanoTime();
        String ip = "192.168.1." + (System.nanoTime() % 255);

        // warmup 100
        for (int i = 0; i < 100; i++) {
            PipelineContext ctx = liveContext("warmup-" + i, ip, roomId);
            stage.process(ctx);
        }

        int samples = 1000;
        int passed = 0;
        int blocked = 0;
        long start = System.nanoTime();

        for (int i = 0; i < samples; i++) {
            // Each request uses a new userId to avoid user-level blocking,
            // keeping IP and room the same to measure the Lua script overhead
            PipelineContext ctx = liveContext("perf-" + System.nanoTime() + "-" + i, ip, roomId);
            stage.process(ctx);
            if (ctx.hasError()) {
                blocked++;
            } else {
                passed++;
            }
        }

        long elapsed = System.nanoTime() - start;
        double avgUs = (elapsed / 1000.0) / samples;

        System.out.println("=== RateLimiter 性能测试 (danmakulive) ===");
        System.out.println("总次数: " + samples);
        System.out.println("放行: " + passed + "  拦截: " + blocked);
        System.out.println("总耗时: " + String.format("%.2f ms", elapsed / 1_000_000.0));
        System.out.println("平均每次: " + String.format("%.1f μs", avgUs));
        System.out.println("(danmakulive 已合并三级限流为单次 Lua 调用，LivePulse 基准: ~1260μs/3次)");
    }

    private static PipelineContext liveContext(String userId, String clientIp, String roomId) {
        PipelineContext ctx = new PipelineContext();
        ctx.setScene(PipelineContext.SCENE_LIVE);
        ctx.setUserId(userId);
        ctx.setClientIp(clientIp);
        ctx.setRoomId(roomId);
        ctx.setRawContent("test content");
        return ctx;
    }
}
