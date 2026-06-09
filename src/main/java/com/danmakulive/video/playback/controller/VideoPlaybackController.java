package com.danmakulive.video.playback.controller;

import com.danmakulive.common.result.Result;
import com.danmakulive.video.playback.service.VideoPlaybackService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/video")
public class VideoPlaybackController {

    private final VideoPlaybackService service;

    public VideoPlaybackController(VideoPlaybackService service) {
        this.service = service;
    }

    @GetMapping("/{id}/play")
    public Result<String> play(@PathVariable String id) {
        return Result.success(service.getPlayUrl(id));
    }
}
