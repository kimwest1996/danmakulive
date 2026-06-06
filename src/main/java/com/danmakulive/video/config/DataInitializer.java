package com.danmakulive.video.config;

import com.danmakulive.video.model.entity.Video;
import com.danmakulive.video.model.mapper.VideoMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final VideoMapper videoMapper;

    public DataInitializer(VideoMapper videoMapper) {
        this.videoMapper = videoMapper;
    }

    @PostConstruct
    public void init() {
        if (videoMapper.selectCount(null) > 0) {
            return;
        }

        seed("Mock-001", "Mock 测试视频 — 精彩集锦", 720);
        seed("Mock-002", "Mock 测试视频 — 完整版", 3600);
        log.info("Seeded 2 mock videos");
    }

    private void seed(String id, String title, int duration) {
        Video v = new Video();
        v.setId(id);
        v.setTitle(title);
        v.setDuration(duration);
        videoMapper.insert(v);
    }
}
