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
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VideoDanmakuService {

    private static final Logger log = LoggerFactory.getLogger(VideoDanmakuService.class);
    private static final String CACHE_PREFIX = "video:danmaku:";
    private static final String META_SUFFIX = ":meta";
    private static final String LOCK_SUFFIX = ":lock";
    private static final Duration LOGIC_EXPIRE = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final DanmakuPipeline pipeline;
    private final VideoDanmakuMapper danmakuMapper;
    private final VideoMapper videoMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Cache<String, Object> videoSegmentCache;

    public VideoDanmakuService(DanmakuPipeline pipeline,
                                VideoDanmakuMapper danmakuMapper,
                                VideoMapper videoMapper,
                                StringRedisTemplate redis,
                                ObjectMapper objectMapper,
                                Cache<String, Object> videoSegmentCache) {
        this.pipeline = pipeline;
        this.danmakuMapper = danmakuMapper;
        this.videoMapper = videoMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.videoSegmentCache = videoSegmentCache;
    }

    public String sendDanmaku(String videoId, String userId, String userName,
                               String clientIp, String content, Double playbackTime) {
        return sendDanmaku(videoId, userId, userName, clientIp, content, playbackTime, false);
    }

    public String sendDanmaku(String videoId, String userId, String userName,
                               String clientIp, String content, Double playbackTime,
                               boolean bypassRateLimit) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new ClientException("视频不存在", BaseErrorCode.NOT_FOUND);
        }
        PipelineContext ctx = new PipelineContext();
        ctx.setScene(PipelineContext.SCENE_VIDEO);
        ctx.setBypassRateLimit(bypassRateLimit);
        ctx.setVideoId(videoId);
        ctx.setUserId(userId);
        ctx.setUserName(userName);
        ctx.setClientIp(clientIp);
        ctx.setRawContent(content);
        ctx.setPlaybackTime(playbackTime);

        pipeline.execute(ctx);

        if (!ctx.hasError() && ctx.getMessage() != null) {
            try {
                String json = objectMapper.writeValueAsString(toSegmentDTO(ctx));
                String key = CACHE_PREFIX + videoId;
                redis.opsForZSet().add(key, json, playbackTime);
                refreshLogicExpire(videoId);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize danmaku for cache", e);
            }
        }

        return ctx.getError();
    }

    // ---------- 逻辑过期 ----------

    /**
     * 刷新逻辑过期时间 = 当前时间 + 24h，写入 meta key
     */
    private void refreshLogicExpire(String videoId) {
        long expireAt = System.currentTimeMillis() + LOGIC_EXPIRE.toMillis();
        redis.opsForValue().set(CACHE_PREFIX + videoId + META_SUFFIX, String.valueOf(expireAt));
    }

    /**
     * 检查逻辑是否已过期。meta key 不存在视为已过期。
     */
    private boolean isLogicExpired(String videoId) {
        String metaValue = redis.opsForValue().get(CACHE_PREFIX + videoId + META_SUFFIX);
        if (metaValue == null) return true;
        return System.currentTimeMillis() > Long.parseLong(metaValue);
    }

    /**
     * 尝试获取互斥锁。成功返回 true，失败返回 false。
     */
    private boolean tryAcquireLock(String videoId) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(CACHE_PREFIX + videoId + LOCK_SUFFIX, "1", LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    // ---------- 查询 ----------

    public List<DanmakuSegmentDTO> getSegments(String videoId, double from, double to) {
        return getSegments(videoId, from, to, "caffeine");
    }

    @SuppressWarnings("unchecked")
    public List<DanmakuSegmentDTO> getSegments(String videoId, double from, double to, String cacheMode) {
        // === Mode 1: pure MySQL, skip all caches ===
        if ("mysql".equals(cacheMode)) {
            double buffer = to - from;
            double queryFrom = Math.max(0, from - buffer);
            double queryTo = to + buffer;
            List<VideoDanmaku> list = danmakuMapper.selectList(
                    new LambdaQueryWrapper<VideoDanmaku>()
                            .eq(VideoDanmaku::getVideoId, videoId)
                            .ge(VideoDanmaku::getPlaybackTime, queryFrom)
                            .le(VideoDanmaku::getPlaybackTime, queryTo)
                            .orderByAsc(VideoDanmaku::getPlaybackTime));
            return list.stream()
                    .filter(dm -> dm.getPlaybackTime() >= from && dm.getPlaybackTime() < to)
                    .map(this::toSegmentDTO)
                    .collect(Collectors.toList());
        }

        // === Mode 2: Caffeine L1 + Redis L2 ===
        if ("caffeine".equals(cacheMode)) {
            int segmentIdx = (int) (from / 60);
            String ck = videoId + ":" + segmentIdx;
            List<DanmakuSegmentDTO> cached = (List<DanmakuSegmentDTO>) videoSegmentCache.getIfPresent(ck);
            if (cached != null) {
                return cached;
            }
            List<DanmakuSegmentDTO> result = redisZsetOrDb(videoId, from, to);
            videoSegmentCache.put(ck, result);
            return result;
        }

        // === Mode 3: Redis ZSET only (default "redis") ===
        return redisZsetOrDb(videoId, from, to);
    }

    private List<DanmakuSegmentDTO> redisZsetOrDb(String videoId, double from, double to) {
        String key = CACHE_PREFIX + videoId;

        // 1. 尝试 ZSET
        Set<String> cached = redis.opsForZSet().rangeByScore(key, from, to - 0.001);
        if (cached != null && !cached.isEmpty()) {
            // 命中，检查逻辑过期
            if (isLogicExpired(videoId) && tryAcquireLock(videoId)) {
                // 过期了，由本线程异步重建，其他线程直接走旧数据
                log.info("Logic expired, async rebuild: videoId={}", videoId);
                CompletableFuture.runAsync(() -> {
                    try {
                        int count = backfillFromDb(videoId, key, from, to);
                        if (count > 0) refreshLogicExpire(videoId);
                    } catch (Exception e) {
                        log.error("Async rebuild failed: videoId={}", videoId, e);
                    }
                });
            }
            return cached.stream()
                    .map(this::deserialize)
                    .sorted(Comparator.comparingDouble(DanmakuSegmentDTO::getPlaybackTime))
                    .collect(Collectors.toList());
        }

        // 2. miss：互斥锁 + 休眠重试（和黑马点评一致）
        //     请求1 → 抢锁 ✅ → 查DB → 回填ZSET → 解锁 → 返回
        //     请求2 → 抢锁 ❌ → sleep 50ms → 重试 → ZSET有了 → 返回
        while (true) {
            if (tryAcquireLock(videoId)) {
                try {
                    // double check：可能前一个线程已经回填好了
                    Set<String> retry = redis.opsForZSet().rangeByScore(key, from, to - 0.001);
                    if (retry != null && !retry.isEmpty()) {
                        return retry.stream()
                                .map(this::deserialize)
                                .sorted(Comparator.comparingDouble(DanmakuSegmentDTO::getPlaybackTime))
                                .collect(Collectors.toList());
                    }
                    List<DanmakuSegmentDTO> result = loadFromDb(videoId, key, from, to);
                    if (!result.isEmpty()) {
                        refreshLogicExpire(videoId);
                    }
                    return result;
                } finally {
                    redis.delete(CACHE_PREFIX + videoId + LOCK_SUFFIX);
                }
            }
            // 没抢到锁，休眠重试
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return Collections.emptyList();
    }

    /**
     * 从 DB 加载并回填 ZSET，返回过滤后的结果。同步调用。
     */
    private List<DanmakuSegmentDTO> loadFromDb(String videoId, String key, double from, double to) {
        double buffer = to - from;
        double queryFrom = Math.max(0, from - buffer);
        double queryTo = to + buffer;

        List<VideoDanmaku> list = danmakuMapper.selectList(
                new LambdaQueryWrapper<VideoDanmaku>()
                        .eq(VideoDanmaku::getVideoId, videoId)
                        .ge(VideoDanmaku::getPlaybackTime, queryFrom)
                        .le(VideoDanmaku::getPlaybackTime, queryTo)
                        .orderByAsc(VideoDanmaku::getPlaybackTime));

        if (!list.isEmpty()) {
            Set<ZSetOperations.TypedTuple<String>> tuples = list.stream()
                    .map(dm -> ZSetOperations.TypedTuple.of(
                            serialize(toSegmentDTO(dm)), dm.getPlaybackTime()))
                    .collect(Collectors.toSet());
            redis.opsForZSet().add(key, tuples);
        }

        return list.stream()
                .filter(dm -> dm.getPlaybackTime() >= from && dm.getPlaybackTime() < to)
                .map(this::toSegmentDTO)
                .collect(Collectors.toList());
    }

    /**
     * 仅回填 DB 数据到 ZSET（不返回结果），用于异步重建。
     */
    private int backfillFromDb(String videoId, String key, double from, double to) {
        double buffer = to - from;
        double queryFrom = Math.max(0, from - buffer);
        double queryTo = to + buffer;

        List<VideoDanmaku> list = danmakuMapper.selectList(
                new LambdaQueryWrapper<VideoDanmaku>()
                        .eq(VideoDanmaku::getVideoId, videoId)
                        .ge(VideoDanmaku::getPlaybackTime, queryFrom)
                        .le(VideoDanmaku::getPlaybackTime, queryTo)
                        .orderByAsc(VideoDanmaku::getPlaybackTime));

        if (!list.isEmpty()) {
            Set<ZSetOperations.TypedTuple<String>> tuples = list.stream()
                    .map(dm -> ZSetOperations.TypedTuple.of(
                            serialize(toSegmentDTO(dm)), dm.getPlaybackTime()))
                    .collect(Collectors.toSet());
            redis.opsForZSet().add(key, tuples);
        }
        return list.size();
    }

    // ---------- 密度 ----------

    public List<DensityDTO> getDensity(String videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new ClientException("视频不存在", BaseErrorCode.NOT_FOUND);
        }
        return danmakuMapper.selectDensity(videoId);
    }

    // ---------- DTO 转换 ----------

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
