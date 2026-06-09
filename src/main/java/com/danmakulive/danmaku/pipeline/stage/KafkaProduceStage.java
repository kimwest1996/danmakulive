package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.danmaku.pipeline.PipelineStage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class KafkaProduceStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(KafkaProduceStage.class);
    private static final String TOPIC = "danmaku";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProduceStage(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(PipelineContext ctx) {
        try {
            String json = objectMapper.writeValueAsString(ctx.getMessage());
            kafkaTemplate.send(TOPIC, ctx.getRoomId(), json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Kafka send failed: roomId={}, danmakuId={}", ctx.getRoomId(), ctx.getMessage().getId(), ex);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize danmaku for Kafka", e);
        }
    }
}
