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

    private static final int USER_MAX = 5;
    private static final int IP_MAX = 20;
    private static final int ROOM_MAX = 1000;
    private static final int WINDOW_SECONDS = 1;

    private static final String LIVE_SCRIPT =
            "local user_cnt = redis.call('INCR', KEYS[1])\n" +
            "if user_cnt == 1 then redis.call('EXPIRE', KEYS[1], ARGV[4]) end\n" +
            "if user_cnt > tonumber(ARGV[1]) then return 1 end\n" +
            "local ip_cnt = redis.call('INCR', KEYS[2])\n" +
            "if ip_cnt == 1 then redis.call('EXPIRE', KEYS[2], ARGV[4]) end\n" +
            "if ip_cnt > tonumber(ARGV[2]) then return 2 end\n" +
            "local room_cnt = redis.call('INCR', KEYS[3])\n" +
            "if room_cnt == 1 then redis.call('EXPIRE', KEYS[3], ARGV[4]) end\n" +
            "if room_cnt > tonumber(ARGV[3]) then return 3 end\n" +
            "return 0";

    private static final String VIDEO_SCRIPT =
            "local ip_cnt = redis.call('INCR', KEYS[1])\n" +
            "if ip_cnt == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end\n" +
            "if ip_cnt > tonumber(ARGV[1]) then return 2 end\n" +
            "local video_cnt = redis.call('INCR', KEYS[2])\n" +
            "if video_cnt == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end\n" +
            "if video_cnt > tonumber(ARGV[2]) then return 3 end\n" +
            "return 0";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> liveScript;
    private final DefaultRedisScript<Long> videoScript;

    public RateLimitStage(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.liveScript = new DefaultRedisScript<>(LIVE_SCRIPT, Long.class);
        this.videoScript = new DefaultRedisScript<>(VIDEO_SCRIPT, Long.class);
    }

    @Override
    public void process(PipelineContext ctx) {
        Long result;
        if (ctx.isVideo()) {
            List<String> keys = List.of(
                    "rate:ip:" + ctx.getClientIp() + ":" + ctx.getVideoId(),
                    "rate:video:" + ctx.getVideoId()
            );
            result = redisTemplate.execute(videoScript, keys,
                    String.valueOf(IP_MAX), String.valueOf(ROOM_MAX),
                    String.valueOf(WINDOW_SECONDS));
        } else {
            List<String> keys = List.of(
                    "rate:user:" + ctx.getUserId() + ":" + ctx.getRoomId(),
                    "rate:ip:" + ctx.getClientIp() + ":" + ctx.getRoomId(),
                    "rate:room:" + ctx.getRoomId()
            );
            result = redisTemplate.execute(liveScript, keys,
                    String.valueOf(USER_MAX), String.valueOf(IP_MAX),
                    String.valueOf(ROOM_MAX), String.valueOf(WINDOW_SECONDS));
        }
        if (result != null && result != 0) {
            ctx.setError("发送频率过快，请稍后再试");
        }
    }
}
