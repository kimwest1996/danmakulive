package com.danmakulive.room.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.room.model.StreamEndedEvent;
import com.danmakulive.room.model.dto.RoomResponse;
import com.danmakulive.room.model.entity.LiveRoom;
import com.danmakulive.room.model.mapper.LiveRoomMapper;
import com.danmakulive.video.model.entity.Video;
import com.danmakulive.video.model.mapper.VideoMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final String TOPIC_STREAM_ENDED = "stream-ended";

    private final LiveRoomMapper roomMapper;
    private final VideoMapper videoMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RoomService(LiveRoomMapper roomMapper, VideoMapper videoMapper,
                       KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.roomMapper = roomMapper;
        this.videoMapper = videoMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public RoomResponse createRoom(String ownerId, String title) {
        LiveRoom room = new LiveRoom();
        room.setTitle(title);
        room.setOwnerId(ownerId);
        room.setStatus(1);
        room.setReplayStatus(0);
        room.setStartedAt(LocalDateTime.now());
        roomMapper.insert(room);
        log.info("Room created: id={}, title={}", room.getId(), title);
        return toResponse(room);
    }

    public List<RoomResponse> listLiveRooms() {
        List<LiveRoom> rooms = roomMapper.selectList(
                new LambdaQueryWrapper<LiveRoom>()
                        .eq(LiveRoom::getStatus, 1)
                        .orderByDesc(LiveRoom::getStartedAt));
        return rooms.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public RoomResponse getRoom(String roomId) {
        LiveRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new ClientException("直播间不存在", BaseErrorCode.NOT_FOUND);
        }
        return toResponse(room);
    }

    public RoomResponse endRoom(String roomId, String ownerId) {
        LiveRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new ClientException("直播间不存在", BaseErrorCode.NOT_FOUND);
        }
        if (!room.getOwnerId().equals(ownerId)) {
            throw new ClientException("只有主播才能结束直播", BaseErrorCode.FORBIDDEN);
        }
        if (room.getStatus() == 2) {
            throw new ClientException("直播已结束");
        }

        // 创建 mock 回放视频
        Video video = new Video();
        video.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        video.setTitle(room.getTitle() + "（回放）");
        video.setDuration(600); // mock: 10分钟回放
        videoMapper.insert(video);

        room.setStatus(2);
        room.setReplayStatus(1);
        room.setReplayVideoId(video.getId());
        room.setEndedAt(LocalDateTime.now());
        roomMapper.updateById(room);

        // 发 Kafka 回放转换事件
        try {
            StreamEndedEvent event = new StreamEndedEvent(roomId, video.getId());
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_STREAM_ENDED, roomId, json);
            log.info("STREAM_ENDED event sent: roomId={}, videoId={}", roomId, video.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize STREAM_ENDED event", e);
        }

        return toResponse(room);
    }

    private RoomResponse toResponse(LiveRoom room) {
        RoomResponse resp = new RoomResponse();
        resp.setId(room.getId());
        resp.setTitle(room.getTitle());
        resp.setOwnerId(room.getOwnerId());
        resp.setStatus(room.getStatus());
        resp.setStartedAt(room.getStartedAt());
        resp.setEndedAt(room.getEndedAt());
        return resp;
    }
}
