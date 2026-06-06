package com.danmakulive.room.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.danmaku.model.entity.LiveDanmaku;
import com.danmakulive.danmaku.model.mapper.LiveDanmakuMapper;
import com.danmakulive.room.model.StreamEndedEvent;
import com.danmakulive.room.model.entity.LiveRoom;
import com.danmakulive.room.model.mapper.LiveRoomMapper;
import com.danmakulive.video.model.entity.VideoDanmaku;
import com.danmakulive.video.model.mapper.VideoDanmakuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class ReplayConverter {

    private static final Logger log = LoggerFactory.getLogger(ReplayConverter.class);

    private final LiveRoomMapper roomMapper;
    private final LiveDanmakuMapper liveDanmakuMapper;
    private final VideoDanmakuMapper videoDanmakuMapper;
    private final ObjectMapper objectMapper;

    public ReplayConverter(LiveRoomMapper roomMapper, LiveDanmakuMapper liveDanmakuMapper,
                            VideoDanmakuMapper videoDanmakuMapper, ObjectMapper objectMapper) {
        this.roomMapper = roomMapper;
        this.liveDanmakuMapper = liveDanmakuMapper;
        this.videoDanmakuMapper = videoDanmakuMapper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "stream-ended", groupId = "replay-converter")
    public void convert(String eventJson) {
        try {
            StreamEndedEvent event = objectMapper.readValue(eventJson, StreamEndedEvent.class);
            log.info("Converting replay: roomId={}, videoId={}", event.getRoomId(), event.getVideoId());

            LiveRoom room = roomMapper.selectById(event.getRoomId());
            if (room == null || room.getReplayStatus() != 1) {
                log.warn("Skip replay conversion: room={}, replayStatus={}",
                        event.getRoomId(), room != null ? room.getReplayStatus() : "null");
                return;
            }

            long startedAtMs = room.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // 分批查询弹幕
            int batchSize = 500;
            int offset = 0;
            int total = 0;
            while (true) {
                var page = liveDanmakuMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(offset / batchSize + 1, batchSize),
                        new LambdaQueryWrapper<LiveDanmaku>()
                                .eq(LiveDanmaku::getRoomId, event.getRoomId())
                                .orderByAsc(LiveDanmaku::getSendTime));
                if (page.getRecords().isEmpty()) break;

                for (LiveDanmaku dm : page.getRecords()) {
                    VideoDanmaku vd = new VideoDanmaku();
                    vd.setId(dm.getId());
                    vd.setVideoId(event.getVideoId());
                    vd.setUserId(dm.getUserId());
                    vd.setUserName(dm.getUserName());
                    vd.setContent(dm.getContent());
                    vd.setPlaybackTime((dm.getSendTime() - startedAtMs) / 1000.0);
                    videoDanmakuMapper.insert(vd);
                    total++;
                }
                offset += page.getRecords().size();
                if (page.getRecords().size() < batchSize) break;
            }

            // 更新回放状态为完成
            room.setReplayStatus(2);
            roomMapper.updateById(room);
            log.info("Replay conversion done: roomId={}, videoId={}, total={}",
                    event.getRoomId(), event.getVideoId(), total);

        } catch (Exception e) {
            log.error("Failed to convert replay: {}", eventJson, e);
        }
    }
}
