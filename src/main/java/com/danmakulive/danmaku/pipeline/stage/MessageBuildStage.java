package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.model.DanmakuMessage;
import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.danmaku.pipeline.PipelineStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Order(3)
public class MessageBuildStage implements PipelineStage {

    @Override
    public void process(PipelineContext ctx) {
        DanmakuMessage msg = new DanmakuMessage();
        msg.setId(UUID.randomUUID().toString());
        msg.setRoomId(ctx.getRoomId());
        msg.setVideoId(ctx.getVideoId());
        msg.setUserId(ctx.getUserId());
        msg.setUserName(ctx.getUserName());
        msg.setContent(ctx.getFilteredContent());
        msg.setSendTime(System.currentTimeMillis());
        msg.setPlaybackTime(ctx.getPlaybackTime());
        ctx.setMessage(msg);
    }
}
