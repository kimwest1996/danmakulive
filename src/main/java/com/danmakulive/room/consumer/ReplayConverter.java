package com.danmakulive.room.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.danmaku.model.entity.LiveDanmaku;
import com.danmakulive.danmaku.model.mapper.LiveDanmakuMapper;
import com.danmakulive.room.model.StreamEndedEvent;
import com.danmakulive.room.model.entity.LiveRoom;
import com.danmakulive.room.model.mapper.LiveRoomMapper;
import com.danmakulive.video.danmaku.model.entity.VideoDanmaku;
import com.danmakulive.video.danmaku.model.mapper.VideoDanmakuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ReplayConverter {

    private static final Logger log = LoggerFactory.getLogger(ReplayConverter.class);

    private static final int BATCH_SIZE = 500;
    private static final int STALE_HOURS = 2;

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

    // ---------------- 共享：分批转换 ----------------

    private int doConvertCore(String roomId, String videoId, long startedAtMs) {
        int offset = 0;
        int total = 0;
        while (true) {
            var page = liveDanmakuMapper.selectPage(
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(offset / BATCH_SIZE + 1, BATCH_SIZE),
                    new LambdaQueryWrapper<LiveDanmaku>()
                            .eq(LiveDanmaku::getRoomId, roomId)
                            .orderByAsc(LiveDanmaku::getSendTime));
            if (page.getRecords().isEmpty()) break;

            for (LiveDanmaku dm : page.getRecords()) {
                VideoDanmaku vd = new VideoDanmaku();
                vd.setId(dm.getId());
                vd.setVideoId(videoId);
                vd.setUserId(dm.getUserId());
                vd.setUserName(dm.getUserName());
                vd.setContent(dm.getContent());
                vd.setPlaybackTime((dm.getSendTime() - startedAtMs) / 1000.0);
                vd.setSendTime(dm.getSendTime());
                videoDanmakuMapper.insert(vd);
                total++;
            }
            offset += page.getRecords().size();
            if (page.getRecords().size() < BATCH_SIZE) break;
        }
        return total;
    }

    // ---------------- 正常流程：Kafka 消息触发 ----------------

    @KafkaListener(topics = "stream-ended", groupId = "replay-converter")
    public void convert(String eventJson) {
        try {
            StreamEndedEvent event = objectMapper.readValue(eventJson, StreamEndedEvent.class);
            log.info("Converting replay: roomId={}, videoId={}", event.getRoomId(), event.getVideoId());

            LiveRoom room = roomMapper.selectById(event.getRoomId());
            if (room == null) {
                log.warn("Skip replay conversion: room not found, roomId={}", event.getRoomId());
                return;
            }
            if (room.getReplayStatus() == 2) {
                log.info("Replay already converted: roomId={}", event.getRoomId());
                return;
            }
            if (room.getReplayStatus() != 1) {
                log.warn("Skip replay conversion: room={}, replayStatus={}", event.getRoomId(), room.getReplayStatus());
                return;
            }

            long startedAtMs = room.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            int total = doConvertCore(event.getRoomId(), event.getVideoId(), startedAtMs);

            room.setReplayStatus(2);
            roomMapper.updateById(room);
            log.info("Replay conversion done: roomId={}, videoId={}, total={}",
                    event.getRoomId(), event.getVideoId(), total);

            int deleted = liveDanmakuMapper.delete(
                    new LambdaQueryWrapper<LiveDanmaku>().eq(LiveDanmaku::getRoomId, event.getRoomId()));
            log.info("Cleaned live_danmaku: roomId={}, deleted={}", event.getRoomId(), deleted);

        } catch (Exception e) {
            log.error("Failed to convert replay: {}", eventJson, e);
        }
    }

    // ---------------- 恢复流程：定时扫描卡死任务 ----------------

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void recoverStaleReplays() {
        var stuckRooms = roomMapper.selectList(
                new LambdaQueryWrapper<LiveRoom>()
                        .eq(LiveRoom::getReplayStatus, 1)
                        .le(LiveRoom::getEndedAt, LocalDateTime.now().minusHours(STALE_HOURS)));
        if (stuckRooms.isEmpty()) return;

        log.warn("Found {} stale replay rooms, attempting recovery", stuckRooms.size());
        for (LiveRoom room : stuckRooms) {
            try {
                String videoId = room.getReplayVideoId();
                if (videoId == null) {
                    log.warn("Skip recovery: no replay_video_id, roomId={}", room.getId());
                    room.setReplayStatus(2);
                    roomMapper.updateById(room);
                    continue;
                }

                // 清空可能存在的脏数据
                int cleaned = videoDanmakuMapper.delete(
                        new LambdaQueryWrapper<VideoDanmaku>().eq(VideoDanmaku::getVideoId, videoId));
                log.info("Recovery: cleaned {} partial records for videoId={}", cleaned, videoId);

                long startedAtMs = room.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                int total = doConvertCore(room.getId(), videoId, startedAtMs);

                room.setReplayStatus(2);
                roomMapper.updateById(room);
                log.info("Recovery done: roomId={}, total={}", room.getId(), total);

                int deleted = liveDanmakuMapper.delete(
                        new LambdaQueryWrapper<LiveDanmaku>().eq(LiveDanmaku::getRoomId, room.getId()));
                log.info("Recovery: cleaned live_danmaku: roomId={}, deleted={}", room.getId(), deleted);

            } catch (Exception e) {
                log.error("Recovery failed for roomId={}", room.getId(), e);
            }
        }
    }
}
