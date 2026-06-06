package com.danmakulive.danmaku.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DanmakuPipeline {

    private final List<PipelineStage> stages;

    public DanmakuPipeline(List<PipelineStage> stages) {
        this.stages = stages;
    }

    public void execute(PipelineContext ctx) {
        for (PipelineStage stage : stages) {
            stage.process(ctx);
            if (ctx.hasError()) {
                return;
            }
        }
    }
}
