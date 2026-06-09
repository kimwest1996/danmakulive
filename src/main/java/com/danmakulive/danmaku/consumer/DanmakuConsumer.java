package com.danmakulive.danmaku.consumer;

import com.danmakulive.danmaku.model.DanmakuMessage;
import com.danmakulive.danmaku.model.entity.LiveDanmaku;
import com.danmakulive.danmaku.model.mapper.LiveDanmakuMapper;
import com.danmakulive.video.danmaku.model.entity.VideoDanmaku;
import com.danmakulive.video.danmaku.model.mapper.VideoDanmakuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DanmakuConsumer {

    private static final Logger log = LoggerFactory.getLogger(DanmakuConsumer.class);

    private final LiveDanmakuMapper liveDanmakuMapper;
    private final VideoDanmakuMapper videoDanmakuMapper;
    private final ObjectMapper objectMapper;

    public DanmakuConsumer(LiveDanmakuMapper liveDanmakuMapper,
                           VideoDanmakuMapper videoDanmakuMapper,
                           ObjectMapper objectMapper) {
        this.liveDanmakuMapper = liveDanmakuMapper;
        this.videoDanmakuMapper = videoDanmakuMapper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "danmaku", groupId = "danmaku-persist-v2")
    public void consume(String messageJson) {
        try {
            DanmakuMessage dm = objectMapper.readValue(messageJson, DanmakuMessage.class);
            if (dm.getVideoId() != null) {
                VideoDanmaku entity = new VideoDanmaku();
                entity.setId(dm.getId());
                entity.setVideoId(dm.getVideoId());
                entity.setUserId(dm.getUserId());
                entity.setUserName(dm.getUserName());
                entity.setContent(dm.getContent());
                entity.setPlaybackTime(dm.getPlaybackTime());
                entity.setSendTime(dm.getSendTime());
                videoDanmakuMapper.insert(entity);
            } else {
                LiveDanmaku entity = new LiveDanmaku();
                entity.setId(dm.getId());
                entity.setRoomId(dm.getRoomId());
                entity.setUserId(dm.getUserId());
                entity.setUserName(dm.getUserName());
                entity.setContent(dm.getContent());
                entity.setSendTime(dm.getSendTime());
                liveDanmakuMapper.insert(entity);
            }
        } catch (Exception e) {
            log.error("Failed to persist danmaku: {}", messageJson, e);
        }
    }
}
