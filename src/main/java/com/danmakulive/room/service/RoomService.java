package com.danmakulive.room.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.room.model.dto.RoomResponse;
import com.danmakulive.room.model.entity.LiveRoom;
import com.danmakulive.room.model.mapper.LiveRoomMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final LiveRoomMapper roomMapper;

    public RoomService(LiveRoomMapper roomMapper) {
        this.roomMapper = roomMapper;
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
        room.setStatus(2);
        room.setReplayStatus(1);
        room.setEndedAt(LocalDateTime.now());
        roomMapper.updateById(room);
        log.info("Room ended: id={}, replay_status=1 (converting)", roomId);
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
