package com.danmakulive.video.upload.controller;

import com.danmakulive.auth.context.UserHolder;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.common.result.Result;
import com.danmakulive.video.upload.model.dto.CheckResponse;
import com.danmakulive.video.upload.model.dto.InitUploadRequest;
import com.danmakulive.video.upload.model.dto.InitUploadResponse;
import com.danmakulive.video.upload.model.dto.MergeResponse;
import com.danmakulive.video.upload.service.VideoUploadService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/video/upload")
public class VideoUploadController {

    private final VideoUploadService service;

    public VideoUploadController(VideoUploadService service) {
        this.service = service;
    }

    @GetMapping("/check")
    public Result<CheckResponse> check(@RequestParam String hash) {
        if (hash == null || hash.length() != 64) {
            throw new ClientException("无效的文件哈希", BaseErrorCode.VALIDATION_ERROR);
        }
        return Result.success(service.check(hash));
    }

    @PostMapping("/init")
    public Result<InitUploadResponse> init(@RequestBody InitUploadRequest request) {
        if (request.getFileHash() == null || request.getFileHash().length() != 64) {
            throw new ClientException("无效的文件哈希", BaseErrorCode.VALIDATION_ERROR);
        }
        if (request.getChunkCount() == null || request.getChunkCount() < 1) {
            throw new ClientException("分块数量不能为空", BaseErrorCode.VALIDATION_ERROR);
        }
        return Result.success(service.initUpload(request));
    }

    @PostMapping("/{uploadId}/merge")
    public Result<MergeResponse> merge(@PathVariable String uploadId) {
        String userId = UserHolder.getUser().getId();
        String videoId = service.merge(uploadId, userId);
        return Result.success(new MergeResponse(videoId));
    }
}
