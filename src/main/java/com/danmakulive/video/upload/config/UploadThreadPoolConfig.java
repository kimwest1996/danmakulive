package com.danmakulive.video.upload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class UploadThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor uploadThreadPool() {
        return new ThreadPoolExecutor(
                5, 10, 30, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(200),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
