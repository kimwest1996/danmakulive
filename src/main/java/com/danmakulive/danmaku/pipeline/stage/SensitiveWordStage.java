package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.danmaku.pipeline.PipelineStage;
import com.danmakulive.danmaku.pipeline.filter.SensitiveWordFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class SensitiveWordStage implements PipelineStage {

    private final SensitiveWordFilter filter;

    public SensitiveWordStage(SensitiveWordFilter filter) {
        this.filter = filter;
    }

    @Override
    public void process(PipelineContext ctx) {
        ctx.setFilteredContent(filter.filter(ctx.getRawContent()));
    }
}
