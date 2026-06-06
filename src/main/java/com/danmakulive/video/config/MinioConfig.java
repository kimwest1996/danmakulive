package com.danmakulive.video.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @org.springframework.beans.factory.annotation.Value("${minio.endpoint}") String endpoint,
            @org.springframework.beans.factory.annotation.Value("${minio.access-key}") String accessKey,
            @org.springframework.beans.factory.annotation.Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
