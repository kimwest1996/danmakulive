package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageBuildStageTest {

    private MessageBuildStage stage;

    @BeforeEach
    void setUp() {
        stage = new MessageBuildStage();
    }

    @Test
    void buildsMessageWithAllFields() {
        PipelineContext ctx = new PipelineContext();
        ctx.setRoomId("room123");
        ctx.setUserId("user456");
        ctx.setUserName("TestUser");
        ctx.setFilteredContent("弹幕内容");

        stage.process(ctx);

        assertNotNull(ctx.getMessage());
        assertNotNull(ctx.getMessage().getId());
        assertEquals("room123", ctx.getMessage().getRoomId());
        assertEquals("user456", ctx.getMessage().getUserId());
        assertEquals("TestUser", ctx.getMessage().getUserName());
        assertEquals("弹幕内容", ctx.getMessage().getContent());
        assertTrue(ctx.getMessage().getSendTime() > 0);
    }

    @Test
    void idIsUnique() {
        PipelineContext ctx1 = new PipelineContext();
        ctx1.setRawContent("a");
        ctx1.setFilteredContent("a");

        PipelineContext ctx2 = new PipelineContext();
        ctx2.setRawContent("b");
        ctx2.setFilteredContent("b");

        stage.process(ctx1);
        stage.process(ctx2);

        assertNotEquals(ctx1.getMessage().getId(), ctx2.getMessage().getId());
    }
}
