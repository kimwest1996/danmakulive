package com.danmakulive.room.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.room.model.dto.RoomResponse;
import com.danmakulive.room.model.entity.LiveRoom;
import com.danmakulive.room.model.mapper.LiveRoomMapper;
import com.danmakulive.video.model.mapper.VideoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomServiceTest {

    private LiveRoomMapper roomMapper;
    private VideoMapper videoMapper;
    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private RoomService roomService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        roomMapper = mock(LiveRoomMapper.class);
        videoMapper = mock(VideoMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        roomService = new RoomService(roomMapper, videoMapper, kafkaTemplate, objectMapper);
    }

    @Test
    void createRoomSuccess() {
        when(roomMapper.insert(any(LiveRoom.class))).thenReturn(1);

        RoomResponse resp = roomService.createRoom("user1", "测试直播间");

        assertEquals("测试直播间", resp.getTitle());
        assertEquals("user1", resp.getOwnerId());
        assertEquals(1, resp.getStatus());
        assertNotNull(resp.getStartedAt());
        verify(roomMapper).insert(any(LiveRoom.class));
    }

    @Test
    void listLiveRoomsOnlyLive() {
        LiveRoom room1 = buildRoom("r1", "Room 1", 1);
        LiveRoom room3 = buildRoom("r3", "Room 3", 1);
        IPage<LiveRoom> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(room1, room3));
        mockPage.setTotal(2);
        doReturn(mockPage).when(roomMapper).selectPage(any(Page.class), any());

        IPage<RoomResponse> result = roomService.listLiveRooms(1, 20);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals("Room 1", result.getRecords().get(0).getTitle());
        assertEquals("Room 3", result.getRecords().get(1).getTitle());
    }

    @Test
    void getRoomFound() {
        LiveRoom room = buildRoom("room123", "My Room", 1);
        when(roomMapper.selectById("room123")).thenReturn(room);

        RoomResponse resp = roomService.getRoom("room123");

        assertEquals("My Room", resp.getTitle());
        assertEquals(1, resp.getStatus());
    }

    @Test
    void getRoomNotFound() {
        when(roomMapper.selectById("nonexistent")).thenReturn(null);

        ClientException ex = assertThrows(ClientException.class,
                () -> roomService.getRoom("nonexistent"));
        assertTrue(ex.getErrorMessage().contains("不存在"));
    }

    @Test
    void endRoomSuccess() {
        LiveRoom room = buildRoom("room123", "My Room", 1);
        room.setOwnerId("user1");
        when(roomMapper.selectById("room123")).thenReturn(room);
        when(roomMapper.updateById(any())).thenReturn(1);

        RoomResponse resp = roomService.endRoom("room123", "user1");

        assertEquals(2, resp.getStatus());
    }

    @Test
    void endRoomNotOwner() {
        LiveRoom room = buildRoom("room123", "My Room", 1);
        room.setOwnerId("user1");
        when(roomMapper.selectById("room123")).thenReturn(room);

        assertThrows(ClientException.class,
                () -> roomService.endRoom("room123", "user2"));
    }

    @Test
    void endRoomAlreadyEnded() {
        LiveRoom room = buildRoom("room123", "My Room", 2);
        room.setOwnerId("user1");
        when(roomMapper.selectById("room123")).thenReturn(room);

        assertThrows(ClientException.class,
                () -> roomService.endRoom("room123", "user1"));
    }

    private LiveRoom buildRoom(String id, String title, int status) {
        LiveRoom room = new LiveRoom();
        room.setId(id);
        room.setTitle(title);
        room.setOwnerId("user1");
        room.setStatus(status);
        room.setReplayStatus(0);
        room.setStartedAt(java.time.LocalDateTime.now());
        return room;
    }
}
