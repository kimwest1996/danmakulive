package com.danmakulive.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class RateLimitStageBenchmark {

    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])

            redis.call('ZREMRANGEBYSCORE', key, 0, now - 1000)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                return 0
            end
            redis.call('ZADD', key, now, now .. ':' .. math.random())
            redis.call('EXPIRE', key, 2)
            return 1
            """;

    private StringRedisTemplate redis;
    private DefaultRedisScript<Long> script;
    private int counter;

    @Setup
    public void setup() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();

        redis = new StringRedisTemplate();
        redis.setConnectionFactory(factory);
        redis.setKeySerializer(StringRedisSerializer.UTF_8);
        redis.setValueSerializer(StringRedisSerializer.UTF_8);
        redis.afterPropertiesSet();

        script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
    }

    @TearDown
    public void tearDown() {
        redis.getConnectionFactory().getConnection().close();
    }

    @Benchmark
    public void luaExecuteFreshKey(Blackhole bh) {
        String key = "bench:rl:user:" + (counter++);
        Long result = redis.execute(script,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                "5");
        bh.consume(result);
    }

    @Benchmark
    public void luaExecuteHotKey(Blackhole bh) {
        // Same key, simulating a hot user hitting their own limit repeatedly
        String key = "bench:rl:user:hot";
        Long result = redis.execute(script,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                "5");
        bh.consume(result);
    }

    @Benchmark
    public void luaExecuteThreeKeys(Blackhole bh) {
        // Simulate user + IP + room triple check (former LivePulse pattern, now merged into one Lua per key)
        // LivePulse did 3 separate Lua calls; danmakulive does 3 calls to 3 different keys with the same script
        int seq = counter++;
        Long r1 = redis.execute(script,
                List.of("bench:rl:triple:user:" + seq),
                String.valueOf(System.currentTimeMillis()), "5");
        Long r2 = redis.execute(script,
                List.of("bench:rl:triple:ip:" + seq),
                String.valueOf(System.currentTimeMillis()), "20");
        Long r3 = redis.execute(script,
                List.of("bench:rl:triple:room:" + seq % 10),
                String.valueOf(System.currentTimeMillis()), "1000");
        bh.consume(r1);
        bh.consume(r2);
        bh.consume(r3);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(RateLimitStageBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
