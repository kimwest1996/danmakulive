package com.danmakulive.video.danmaku.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.danmaku.pipeline.DanmakuPipeline;
import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.video.danmaku.model.dto.DanmakuSegmentDTO;
import com.danmakulive.video.danmaku.model.dto.DensityDTO;
import com.danmakulive.video.model.entity.Video;
import com.danmakulive.video.danmaku.model.entity.VideoDanmaku;
import com.danmakulive.video.danmaku.model.mapper.VideoDanmakuMapper;
import com.danmakulive.video.model.mapper.VideoMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoDanmakuService {

    private static final Logger log = LoggerFactory.getLogger(VideoDanmakuService.class);
    private static final int SEGMENT_SECONDS = 60;
    private static final String CACHE_PREFIX = "video:danmaku:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final DanmakuPipeline pipeline;
    private final VideoDanmakuMapper danmakuMapper;
    private final VideoMapper videoMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public VideoDanmakuService(DanmakuPipeline pipeline,
                                VideoDanmakuMapper danmakuMapper,
                                VideoMapper videoMapper,
                                StringRedisTemplate redis,
                                ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.danmakuMapper = danmakuMapper;
        this.videoMapper = videoMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String sendDanmaku(String videoId, String userId, String userName,
                               String clientIp, String content, Double playbackTime) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new ClientException("视频不存在", BaseErrorCode.NOT_FOUND);
        }
        PipelineContext ctx = new PipelineContext();
        ctx.setScene(PipelineContext.SCENE_VIDEO);
        ctx.setVideoId(videoId);
        ctx.setUserId(userId);
        ctx.setUserName(userName);
        ctx.setClientIp(clientIp);
        ctx.setRawContent(content);
        ctx.setPlaybackTime(playbackTime);

        pipeline.execute(ctx);

        // 追加到 ZSET 缓存
        if (!ctx.hasError() && ctx.getMessage() != null) {
            try {
                String json = objectMapper.writeValueAsString(toSegmentDTO(ctx));
                String key = CACHE_PREFIX + videoId;
                redis.opsForZSet().add(key, json, playbackTime);
                redis.expire(key, CACHE_TTL);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize danmaku for cache", e);
            }
        }

        return ctx.getError();
    }

    public List<DanmakuSegmentDTO> getSegments(String videoId, double from, double to) {
        String key = CACHE_PREFIX + videoId;
        // 1. 尝试缓存
        Set<String> cached = redis.opsForZSet().rangeByScore(key, from, to - 0.001);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream()
                    .map(this::deserialize)
                    .sorted(Comparator.comparingDouble(DanmakuSegmentDTO::getPlaybackTime))
                    .collect(Collectors.toList());
        }

        // 2. 查 MySQL 全量弹幕
        List<VideoDanmaku> all = danmakuMapper.selectList(
                new LambdaQueryWrapper<VideoDanmaku>()
                        .eq(VideoDanmaku::getVideoId, videoId)
                        .orderByAsc(VideoDanmaku::getPlaybackTime));

        // 3. 回种 ZSET
        if (!all.isEmpty()) {
            Set<ZSetOperations.TypedTuple<String>> tuples = all.stream()
                    .map(dm -> ZSetOperations.TypedTuple.of(
                            serialize(toSegmentDTO(dm)), dm.getPlaybackTime()))
                    .collect(Collectors.toSet());
            redis.opsForZSet().add(key, tuples);
            redis.expire(key, CACHE_TTL);
        }

        // 4. 返回请求区间
        return all.stream()
                .filter(dm -> dm.getPlaybackTime() >= from && dm.getPlaybackTime() < to)
                .map(this::toSegmentDTO)
                .collect(Collectors.toList());
    }

    public List<DensityDTO> getDensity(String videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new ClientException("视频不存在", BaseErrorCode.NOT_FOUND);
        }

        List<VideoDanmaku> all = danmakuMapper.selectList(
                new LambdaQueryWrapper<VideoDanmaku>()
                        .eq(VideoDanmaku::getVideoId, videoId)
                        .orderByAsc(VideoDanmaku::getPlaybackTime));

        int segments = (video.getDuration() / SEGMENT_SECONDS) + 1;
        int[] counts = new int[segments];
        for (VideoDanmaku dm : all) {
            int seg = (int) (dm.getPlaybackTime() / SEGMENT_SECONDS);
            if (seg < segments) {
                counts[seg]++;
            }
        }

        List<DensityDTO> result = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            if (counts[i] > 0) {
                result.add(new DensityDTO(i * SEGMENT_SECONDS, counts[i]));
            }
        }
        return result;
    }

    private DanmakuSegmentDTO toSegmentDTO(VideoDanmaku dm) {
        DanmakuSegmentDTO dto = new DanmakuSegmentDTO();
        dto.setId(dm.getId());
        dto.setUserId(dm.getUserId());
        dto.setUserName(dm.getUserName());
        dto.setContent(dm.getContent());
        dto.setPlaybackTime(dm.getPlaybackTime());
        return dto;
    }

    private DanmakuSegmentDTO toSegmentDTO(PipelineContext ctx) {
        DanmakuSegmentDTO dto = new DanmakuSegmentDTO();
        dto.setId(ctx.getMessage().getId());
        dto.setUserId(ctx.getUserId());
        dto.setUserName(ctx.getUserName());
        dto.setContent(ctx.getFilteredContent());
        dto.setPlaybackTime(ctx.getPlaybackTime());
        return dto;
    }

    private String serialize(DanmakuSegmentDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private DanmakuSegmentDTO deserialize(String json) {
        try {
            return objectMapper.readValue(json, DanmakuSegmentDTO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
