package com.danmakulive.video.danmaku.controller;

import com.danmakulive.auth.context.UserHolder;
import com.danmakulive.auth.model.dto.UserDTO;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.common.result.Result;
import com.danmakulive.video.danmaku.model.dto.DanmakuSegmentDTO;
import com.danmakulive.video.danmaku.model.dto.DensityDTO;
import com.danmakulive.video.danmaku.model.dto.VideoDanmakuRequest;
import com.danmakulive.video.danmaku.service.VideoDanmakuService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/video")
public class VideoDanmakuController {

    private final VideoDanmakuService service;

    public VideoDanmakuController(VideoDanmakuService service) {
        this.service = service;
    }

    @PostMapping("/{videoId}/danmaku")
    public Result<Void> sendDanmaku(@PathVariable String videoId,
                                     @RequestBody VideoDanmakuRequest request,
                                     HttpServletRequest httpRequest) {
        UserDTO user = UserHolder.getUser();
        String content = request.getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new ClientException("弹幕内容不能为空", BaseErrorCode.VALIDATION_ERROR);
        }
        if (content.trim().length() > 200) {
            throw new ClientException("弹幕内容过长", BaseErrorCode.VALIDATION_ERROR);
        }
        if (request.getPlaybackTime() == null || request.getPlaybackTime() < 0) {
            throw new ClientException("时间点无效", BaseErrorCode.VALIDATION_ERROR);
        }

        String clientIp = httpRequest.getRemoteAddr();
        String error = service.sendDanmaku(
                videoId, user.getId(), user.getNickName(), clientIp,
                content.trim(), request.getPlaybackTime());

        if (error != null) {
            return Result.failure("RATE_LIMITED", error);
        }
        return Result.success();
    }

    @GetMapping("/{videoId}/danmaku/segments")
    public Result<List<DanmakuSegmentDTO>> getSegments(
            @PathVariable String videoId,
            @RequestParam double from,
            @RequestParam double to) {
        return Result.success(service.getSegments(videoId, from, to));
    }

    @GetMapping("/{videoId}/danmaku/density")
    public Result<List<DensityDTO>> getDensity(@PathVariable String videoId) {
        return Result.success(service.getDensity(videoId));
    }
}
