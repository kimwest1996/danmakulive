package com.danmakulive.danmaku.service;

import com.danmakulive.danmaku.pipeline.DanmakuPipeline;
import com.danmakulive.danmaku.pipeline.PipelineContext;
import org.springframework.stereotype.Service;

@Service
public class DanmakuService {

    private final DanmakuPipeline pipeline;

    public DanmakuService(DanmakuPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public String processDanmaku(String roomId, String userId, String userName,
                                  String clientIp, String content) {
        return processDanmaku(roomId, userId, userName, clientIp, content, false);
    }

    public String processDanmaku(String roomId, String userId, String userName,
                                  String clientIp, String content, boolean bypassRateLimit) {
        PipelineContext ctx = new PipelineContext();
        ctx.setRoomId(roomId);
        ctx.setUserId(userId);
        ctx.setUserName(userName);
        ctx.setClientIp(clientIp);
        ctx.setRawContent(content);
        ctx.setBypassRateLimit(bypassRateLimit);

        pipeline.execute(ctx);

        return ctx.getError();
    }
}
