package com.danmakulive.video.upload.scheduler;

import com.danmakulive.video.upload.service.VideoUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChunkCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChunkCleanupScheduler.class);

    private final VideoUploadService uploadService;

    public ChunkCleanupScheduler(VideoUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void cleanupStaleChunks() {
        log.debug("Running scheduled chunk cleanup...");
        int count = uploadService.cleanupStaleChunks();
        if (count > 0) {
            log.info("Scheduled cleanup completed: {} uploads cleaned", count);
        }
    }
}
