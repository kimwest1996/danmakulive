package com.danmakulive.danmaku.service;

import com.danmakulive.danmaku.pipeline.DanmakuPipeline;
import com.danmakulive.danmaku.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DanmakuServiceTest {

    private DanmakuPipeline pipeline;
    private DanmakuService service;

    @BeforeEach
    void setUp() {
        pipeline = mock(DanmakuPipeline.class);
        service = new DanmakuService(pipeline);
    }

    @Test
    void processDanmakuReturnsNullOnSuccess() {
        doAnswer(inv -> {
            PipelineContext ctx = inv.getArgument(0);
            ctx.setFilteredContent("filtered");
            return null;
        }).when(pipeline).execute(any(PipelineContext.class));

        String error = service.processDanmaku("room1", "user1", "TestUser", "127.0.0.1", "hello");

        assertNull(error);
        verify(pipeline).execute(any(PipelineContext.class));
    }

    @Test
    void processDanmakuReturnsError() {
        doAnswer(inv -> {
            PipelineContext ctx = inv.getArgument(0);
            ctx.setError("rate limited");
            return null;
        }).when(pipeline).execute(any(PipelineContext.class));

        String error = service.processDanmaku("room1", "user1", "TestUser", "127.0.0.1", "hello");

        assertEquals("rate limited", error);
    }

    @Test
    void processDanmakuPopulatesContextCorrectly() {
        final PipelineContext[] captured = new PipelineContext[1];
        doAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return null;
        }).when(pipeline).execute(any(PipelineContext.class));

        service.processDanmaku("room1", "user1", "TestUser", "192.168.1.1", "hello world");

        assertEquals("room1", captured[0].getRoomId());
        assertEquals("user1", captured[0].getUserId());
        assertEquals("TestUser", captured[0].getUserName());
        assertEquals("192.168.1.1", captured[0].getClientIp());
        assertEquals("hello world", captured[0].getRawContent());
    }
}
