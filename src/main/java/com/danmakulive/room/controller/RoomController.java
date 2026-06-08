package com.danmakulive.room.controller;

import com.danmakulive.auth.context.UserHolder;
import com.danmakulive.auth.model.dto.UserDTO;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.common.result.Result;
import com.danmakulive.room.model.dto.CreateRoomRequest;
import com.danmakulive.room.model.dto.RoomResponse;
import com.danmakulive.room.service.RoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public Result<RoomResponse> createRoom(@RequestBody CreateRoomRequest request) {
        UserDTO user = UserHolder.getUser();
        String title = request.getTitle();
        if (title == null || title.trim().isEmpty()) {
            throw new ClientException("直播间标题不能为空", BaseErrorCode.VALIDATION_ERROR);
        }
        if (title.trim().length() > 128) {
            throw new ClientException("标题过长", BaseErrorCode.VALIDATION_ERROR);
        }
        return Result.success(roomService.createRoom(user.getId(), title.trim()));
    }

    @GetMapping
    public Result<List<RoomResponse>> listRooms() {
        return Result.success(roomService.listLiveRooms());
    }

    @GetMapping("/{roomId}")
    public Result<RoomResponse> getRoom(@PathVariable String roomId) {
        return Result.success(roomService.getRoom(roomId));
    }

    @PostMapping("/{roomId}/end")
    public Result<RoomResponse> endRoom(@PathVariable String roomId) {
        UserDTO user = UserHolder.getUser();
        return Result.success(roomService.endRoom(roomId, user.getId()));
    }
}
