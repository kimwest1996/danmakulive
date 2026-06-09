package com.danmakulive.video.playback.service;

import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.video.model.entity.Video;
import com.danmakulive.video.model.mapper.VideoMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class VideoPlaybackService {

    private static final Logger log = LoggerFactory.getLogger(VideoPlaybackService.class);
    private static final String BUCKET = "danmakulive-videos";
    private static final int URL_EXPIRY_HOURS = 2;

    private final VideoMapper videoMapper;
    private final MinioClient minio;

    public VideoPlaybackService(VideoMapper videoMapper, MinioClient minio) {
        this.videoMapper = videoMapper;
        this.minio = minio;
    }

    public String getPlayUrl(String videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new ClientException("视频不存在", BaseErrorCode.NOT_FOUND);
        }
        if (video.getObjectKey() == null) {
            throw new ClientException("视频文件不存在", BaseErrorCode.NOT_FOUND);
        }

        try {
            String url = minio.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(BUCKET)
                            .object(video.getObjectKey())
                            .expiry(URL_EXPIRY_HOURS, TimeUnit.HOURS)
                            .build());
            log.info("Generated play URL: videoId={}, object={}", videoId, video.getObjectKey());
            return url;
        } catch (Exception e) {
            log.error("Failed to generate play URL: videoId={}", videoId, e);
            throw new RuntimeException("生成播放链接失败", e);
        }
    }
}
