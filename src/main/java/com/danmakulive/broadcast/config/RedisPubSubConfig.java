package com.danmakulive.broadcast.config;

import com.danmakulive.broadcast.RedisBroadcastListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class RedisPubSubConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubConfig.class);

    @Bean
    public AtomicLong broadcastDropCount() {
        return new AtomicLong(0);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisBroadcastListener listener,
            AtomicLong broadcastDropCount) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setRejectedExecutionHandler((r, e) -> {
            broadcastDropCount.incrementAndGet();
            throw new TaskRejectedException("Broadcast queue full, dropped=" + broadcastDropCount.get());
        });
        executor.setThreadNamePrefix("broadcast-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        container.setTaskExecutor(executor);

        container.addMessageListener(listener, new PatternTopic("room:*:pubsub"));
        container.addMessageListener(listener, new PatternTopic("video:*:pubsub"));
        return container;
    }
}
