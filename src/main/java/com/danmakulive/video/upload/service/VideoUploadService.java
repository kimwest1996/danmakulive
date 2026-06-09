package com.danmakulive.video.upload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.video.upload.model.dto.CheckResponse;
import com.danmakulive.video.upload.model.dto.InitUploadRequest;
import com.danmakulive.video.upload.model.dto.InitUploadResponse;
import com.danmakulive.video.model.entity.Video;
import com.danmakulive.video.upload.model.entity.VideoUpload;
import com.danmakulive.video.model.mapper.VideoMapper;
import com.danmakulive.video.upload.model.mapper.VideoUploadMapper;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class VideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadService.class);
    static final String BUCKET = "danmakulive-videos";
    private static final int PRESIGNED_MINUTES = 15;

    private final VideoUploadMapper uploadMapper;
    private final VideoMapper videoMapper;
    private final MinioClient minio;

    public VideoUploadService(VideoUploadMapper uploadMapper, VideoMapper videoMapper,
                               MinioClient minio) {
        this.uploadMapper = uploadMapper;
        this.videoMapper = videoMapper;
        this.minio = minio;
    }

    public CheckResponse check(String hash) {
        VideoUpload existing = uploadMapper.selectOne(
                new LambdaQueryWrapper<VideoUpload>().eq(VideoUpload::getFileHash, hash));
        if (existing == null) {
            return CheckResponse.notFound();
        }
        if (existing.getStatus() == 0) {
            return CheckResponse.uploading(existing.getId());
        }
        return CheckResponse.uploaded(existing.getVideoId());
    }

    public InitUploadResponse initUpload(InitUploadRequest req) {
        // 检查 hash 冲突
        VideoUpload existing = uploadMapper.selectOne(
                new LambdaQueryWrapper<VideoUpload>().eq(VideoUpload::getFileHash, req.getFileHash()));
        if (existing != null) {
            if (existing.getStatus() == 1) {
                throw new ClientException("文件已存在", BaseErrorCode.DUPLICATE);
            }
            throw new ClientException("该文件正在上传中", BaseErrorCode.CLIENT_ERROR);
        }

        String uploadId = UUID.randomUUID().toString();
        String objectPrefix = req.getFileHash() + "/";

        // 确保 bucket 存在
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
                log.info("Created bucket: {}", BUCKET);
            }
        } catch (Exception e) {
            log.error("MinIO bucket init failed", e);
            throw new RuntimeException("MinIO bucket 初始化失败", e);
        }

        // 生成 presigned URL
        List<InitUploadResponse.PresignedUrl> urls = new ArrayList<>();
        for (int i = 0; i < req.getChunkCount(); i++) {
            String objectName = objectPrefix + i;
            try {
                String url = minio.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.PUT)
                                .bucket(BUCKET)
                                .object(objectName)
                                .expiry(PRESIGNED_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
                                .build());
                urls.add(new InitUploadResponse.PresignedUrl(i, url));
            } catch (Exception e) {
                log.error("Presigned URL generation failed", e);
                throw new RuntimeException("生成上传 URL 失败", e);
            }
        }

        // 持久化
        VideoUpload upload = new VideoUpload();
        upload.setId(uploadId);
        upload.setFileHash(req.getFileHash());
        upload.setFileName(req.getFileName());
        upload.setFileSize(req.getFileSize());
        upload.setChunkCount(req.getChunkCount());
        upload.setStatus(0);
        upload.setBucketName(BUCKET);
        upload.setObjectPath(objectPrefix);
        uploadMapper.insert(upload);

        InitUploadResponse resp = new InitUploadResponse();
        resp.setUploadId(uploadId);
        resp.setPresignedUrls(urls);
        resp.setBucketName(BUCKET);
        resp.setObjectPrefix(objectPrefix);
        return resp;
    }

    public String merge(String uploadId, String ownerId) {
        VideoUpload upload = uploadMapper.selectById(uploadId);
        if (upload == null) {
            throw new ClientException("上传任务不存在", BaseErrorCode.NOT_FOUND);
        }
        if (upload.getStatus() == 1) {
            throw new ClientException("已合并完成");
        }

        // composeObject: 合并所有分块
        List<ComposeSource> sources = new ArrayList<>();
        for (int i = 0; i < upload.getChunkCount(); i++) {
            sources.add(ComposeSource.builder()
                    .bucket(BUCKET)
                    .object(upload.getObjectPath() + i)
                    .build());
        }
        String mergedObject = upload.getObjectPath() + "merged.mp4";
        try {
            minio.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(BUCKET)
                            .object(mergedObject)
                            .sources(sources)
                            .build());
        } catch (Exception e) {
            log.error("composeObject failed: uploadId={}", uploadId, e);
            throw new RuntimeException("合并上传文件失败", e);
        }

        // 创建 video 记录
        Video video = new Video();
        video.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        video.setTitle(upload.getFileName());
        video.setDuration(600);
        video.setOwnerId(ownerId);
        video.setObjectKey(mergedObject);
        videoMapper.insert(video);

        // 更新 upload 状态
        upload.setStatus(1);
        upload.setVideoId(video.getId());
        uploadMapper.updateById(upload);
        log.info("Upload merged: uploadId={}, videoId={}", uploadId, video.getId());

        // 清理分块 — fire-and-forget，不阻塞返回
        CompletableFuture.runAsync(() -> deleteChunks(upload));

        return video.getId();
    }

    void deleteChunks(VideoUpload upload) {
        for (int i = 0; i < upload.getChunkCount(); i++) {
            try {
                minio.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(BUCKET)
                                .object(upload.getObjectPath() + i)
                                .build());
            } catch (Exception ignored) {
            }
        }
    }

    public int cleanupStaleChunks() {
        List<VideoUpload> uploads = uploadMapper.selectList(
                new LambdaQueryWrapper<VideoUpload>()
                        .eq(VideoUpload::getStatus, 1)
                        .ge(VideoUpload::getCreateTime, LocalDateTime.now().minusHours(2)));
        for (VideoUpload upload : uploads) {
            deleteChunks(upload);
        }
        if (!uploads.isEmpty()) {
            log.info("Cleaned up {} stale upload chunks", uploads.size());
        }
        return uploads.size();
    }

}
