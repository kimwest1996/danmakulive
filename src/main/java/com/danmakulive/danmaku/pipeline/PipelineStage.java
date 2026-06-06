package com.danmakulive.danmaku.pipeline;

public interface PipelineStage {
    void process(PipelineContext ctx);
}
