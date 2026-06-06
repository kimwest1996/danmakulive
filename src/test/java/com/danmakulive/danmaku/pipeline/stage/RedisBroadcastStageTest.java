package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.model.DanmakuMessage;
import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisBroadcastStageTest {

    private StringRedisTemplate redis;
    private ObjectMapper objectMapper;
    private RedisBroadcastStage stage;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        stage = new RedisBroadcastStage(redis, objectMapper);
    }

    @Test
    void publishesToCorrectChannel() {
        PipelineContext ctx = new PipelineContext();
        ctx.setRoomId("room123");
        DanmakuMessage msg = new DanmakuMessage();
        msg.setId("id1");
        msg.setRoomId("room123");
        msg.setContent("test");
        msg.setSendTime(1000L);
        ctx.setMessage(msg);

        stage.process(ctx);

        assertNull(ctx.getError());
        verify(redis).convertAndSend(eq("room:room123:pubsub"), anyString());
    }

    @Test
    void publishesNullAsStringWhenMessageIsNull() {
        PipelineContext ctx = new PipelineContext();
        ctx.setRoomId("room123");
        // message is null — should not throw, delegates to Jackson's null handling

        stage.process(ctx);

        // ObjectMapper serializes null to "null" string, no error expected
        assertNull(ctx.getError());
        verify(redis).convertAndSend(eq("room:room123:pubsub"), eq("null"));
    }
}
