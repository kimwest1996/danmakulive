package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitStageTest {

    private StringRedisTemplate redis;
    private RateLimitStage stage;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        stage = new RateLimitStage(redis);
    }

    @Test
    void passWhenAllUnderLimit() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        PipelineContext ctx = buildContext("room1", "user1", "127.0.0.1");
        stage.process(ctx);
        assertNull(ctx.getError());
    }

    @Test
    void rejectWhenUserOverLimit() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        PipelineContext ctx = buildContext("room1", "user1", "127.0.0.1");
        stage.process(ctx);
        assertNotNull(ctx.getError());
        assertTrue(ctx.getError().contains("频率过快"));
    }

    @Test
    void rejectWhenIpOverLimit() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(2L);

        PipelineContext ctx = buildContext("room1", "user1", "127.0.0.1");
        stage.process(ctx);
        assertNotNull(ctx.getError());
    }

    @Test
    void rejectWhenRoomOverLimit() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(3L);

        PipelineContext ctx = buildContext("room1", "user1", "127.0.0.1");
        stage.process(ctx);
        assertNotNull(ctx.getError());
    }

    private PipelineContext buildContext(String roomId, String userId, String ip) {
        PipelineContext ctx = new PipelineContext();
        ctx.setRoomId(roomId);
        ctx.setUserId(userId);
        ctx.setClientIp(ip);
        return ctx;
    }
}
