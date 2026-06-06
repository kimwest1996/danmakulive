package com.danmakulive.video.service;

import com.danmakulive.danmaku.pipeline.DanmakuPipeline;
import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.video.model.entity.Video;
import com.danmakulive.video.model.entity.VideoDanmaku;
import com.danmakulive.video.model.mapper.VideoDanmakuMapper;
import com.danmakulive.video.model.mapper.VideoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoDanmakuServiceTest {

    private DanmakuPipeline pipeline;
    private VideoDanmakuMapper danmakuMapper;
    private VideoMapper videoMapper;
    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> zSetOps;
    private VideoDanmakuService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        pipeline = mock(DanmakuPipeline.class);
        danmakuMapper = mock(VideoDanmakuMapper.class);
        videoMapper = mock(VideoMapper.class);
        redis = mock(StringRedisTemplate.class);
        zSetOps = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);
        service = new VideoDanmakuService(pipeline, danmakuMapper, videoMapper,
                redis, new ObjectMapper());
    }

    @Test
    void sendDanmakuSetsVideoScene() {
        Video video = new Video();
        video.setId("Mock-001");
        video.setDuration(720);
        when(videoMapper.selectById("Mock-001")).thenReturn(video);

        service.sendDanmaku("Mock-001", "user1", "Tester", "127.0.0.1", "你好", 120.0);

        verify(pipeline).execute(any(PipelineContext.class));
    }

    @Test
    void sendDanmakuVideoNotFound() {
        when(videoMapper.selectById("bad-id")).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                service.sendDanmaku("bad-id", "user1", "Tester", "127.0.0.1", "hi", 0.0));
    }

    @Test
    void getSegmentsReturnsCorrectOrder() {
        VideoDanmaku dm1 = buildDanmaku("d1", 10.5, "aaa");
        VideoDanmaku dm2 = buildDanmaku("d2", 25.0, "bbb");
        when(danmakuMapper.selectList(any())).thenReturn(List.of(dm1, dm2));

        var result = service.getSegments("Mock-001", 0, 60);

        assertEquals(2, result.size());
        assertEquals(10.5, result.get(0).getPlaybackTime());
        assertEquals(25.0, result.get(1).getPlaybackTime());
    }

    private VideoDanmaku buildDanmaku(String id, double time, String content) {
        VideoDanmaku dm = new VideoDanmaku();
        dm.setId(id);
        dm.setVideoId("Mock-001");
        dm.setUserId("user1");
        dm.setUserName("Tester");
        dm.setContent(content);
        dm.setPlaybackTime(time);
        return dm;
    }
}
