package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.danmaku.pipeline.PipelineStage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class RedisBroadcastStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastStage.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisBroadcastStage(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(PipelineContext ctx) {
        try {
            String json = objectMapper.writeValueAsString(ctx.getMessage());
            String channel;
            if (ctx.isVideo()) {
                channel = "video:" + ctx.getVideoId() + ":pubsub";
            } else {
                channel = "room:" + ctx.getRoomId() + ":pubsub";
            }
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize danmaku message", e);
            ctx.setError("消息序列化失败");
        }
    }
}
