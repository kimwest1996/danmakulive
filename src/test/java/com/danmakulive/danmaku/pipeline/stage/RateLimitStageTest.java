package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

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
        // ZSET sliding window: 1L = pass
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        PipelineContext ctx = buildLiveContext("room1", "user1", "127.0.0.1");
        stage.process(ctx);
        assertNull(ctx.getError());
    }

    @Test
    void rejectWhenUserOverLimit() {
        // First call (user) returns 0L = rejected, short-circuits before IP and room
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L);

        PipelineContext ctx = buildLiveContext("room1", "user1", "127.0.0.1");
        stage.process(ctx);
        assertNotNull(ctx.getError());
        assertTrue(ctx.getError().contains("频率过快"));
    }

    @Test
    void videoSkipsUserTier() {
        // Both IP and video pass
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        PipelineContext ctx = buildVideoContext("video1", "127.0.0.1");
        stage.process(ctx);
        assertNull(ctx.getError());
        // Only 2 calls (IP + video), not 3
        verify(redis, times(2)).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString());
    }

    @Test
    void videoRejectWhenIpOverLimit() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L);

        PipelineContext ctx = buildVideoContext("video1", "127.0.0.1");
        stage.process(ctx);
        assertNotNull(ctx.getError());
        // Only IP check executed (short-circuits)
        verify(redis, times(1)).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString());
    }

    private PipelineContext buildLiveContext(String roomId, String userId, String ip) {
        PipelineContext ctx = new PipelineContext();
        ctx.setScene(PipelineContext.SCENE_LIVE);
        ctx.setRoomId(roomId);
        ctx.setUserId(userId);
        ctx.setClientIp(ip);
        return ctx;
    }

    private PipelineContext buildVideoContext(String videoId, String ip) {
        PipelineContext ctx = new PipelineContext();
        ctx.setScene(PipelineContext.SCENE_VIDEO);
        ctx.setVideoId(videoId);
        ctx.setClientIp(ip);
        return ctx;
    }
}
