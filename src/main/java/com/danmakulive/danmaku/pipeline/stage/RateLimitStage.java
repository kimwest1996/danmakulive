package com.danmakulive.danmaku.pipeline.stage;

import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.danmaku.pipeline.PipelineStage;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
public class RateLimitStage implements PipelineStage {

    private static final int USER_LIMIT = 5;
    private static final int IP_LIMIT = 20;
    private static final int ROOM_LIMIT = 1000;
    private static final long WINDOW_MS = 1000;

    // ZSET sliding window: ZREMRANGEBYSCORE removes expired entries, ZCARD counts current, ZADD appends
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])

            redis.call('ZREMRANGEBYSCORE', key, 0, now - %d)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                return 0
            end
            redis.call('ZADD', key, now, now .. ':' .. math.random())
            redis.call('EXPIRE', key, %d)
            return 1
            """.formatted(WINDOW_MS, WINDOW_MS / 1000 + 1);

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RateLimitStage(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
    }

    @Override
    public void process(PipelineContext ctx) {
        long now = System.currentTimeMillis();
        if (ctx.isVideo()) {
            if (!tryAcquire("rl:ip:" + ctx.getClientIp(), now, IP_LIMIT) ||
                    !tryAcquire("rl:video:" + ctx.getVideoId(), now, ROOM_LIMIT)) {
                ctx.setError("发送频率过快，请稍后再试");
            }
        } else {
            if (!tryAcquire("rl:user:" + ctx.getUserId(), now, USER_LIMIT) ||
                    !tryAcquire("rl:ip:" + ctx.getClientIp(), now, IP_LIMIT) ||
                    !tryAcquire("rl:room:" + ctx.getRoomId(), now, ROOM_LIMIT)) {
                ctx.setError("发送频率过快，请稍后再试");
            }
        }
    }

    private boolean tryAcquire(String key, long now, int limit) {
        Long result = redis.execute(script,
                List.of(key),
                String.valueOf(now),
                String.valueOf(limit));
        return result != null && result == 1L;
    }
}
