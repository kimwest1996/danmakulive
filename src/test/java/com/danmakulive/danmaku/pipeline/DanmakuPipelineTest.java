package com.danmakulive.danmaku.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DanmakuPipelineTest {

    private PipelineStage stage1;
    private PipelineStage stage2;
    private PipelineStage stage3;
    private DanmakuPipeline pipeline;

    @BeforeEach
    void setUp() {
        stage1 = mock(PipelineStage.class);
        stage2 = mock(PipelineStage.class);
        stage3 = mock(PipelineStage.class);
        pipeline = new DanmakuPipeline(List.of(stage1, stage2, stage3));
    }

    @Test
    void executesAllStagesInOrder() {
        PipelineContext ctx = new PipelineContext();

        pipeline.execute(ctx);

        var inOrder = inOrder(stage1, stage2, stage3);
        inOrder.verify(stage1).process(ctx);
        inOrder.verify(stage2).process(ctx);
        inOrder.verify(stage3).process(ctx);
    }

    @Test
    void shortCircuitsOnError() {
        PipelineContext ctx = new PipelineContext();
        doAnswer(inv -> {
            ctx.setError("rate limited");
            return null;
        }).when(stage1).process(ctx);

        pipeline.execute(ctx);

        verify(stage1).process(ctx);
        verify(stage2, never()).process(ctx);
        verify(stage3, never()).process(ctx);
        assertEquals("rate limited", ctx.getError());
    }
}
