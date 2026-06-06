package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.danmaku.pipeline.filter.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SensitiveWordStageTest {

    private SensitiveWordFilter filter;
    private SensitiveWordStage stage;

    @BeforeEach
    void setUp() {
        filter = mock(SensitiveWordFilter.class);
        stage = new SensitiveWordStage(filter);
    }

    @Test
    void filterCalledAndResultStored() {
        when(filter.filter("原始内容")).thenReturn("过滤后内容");

        PipelineContext ctx = new PipelineContext();
        ctx.setRawContent("原始内容");
        stage.process(ctx);

        assertEquals("过滤后内容", ctx.getFilteredContent());
        verify(filter).filter("原始内容");
    }
}
