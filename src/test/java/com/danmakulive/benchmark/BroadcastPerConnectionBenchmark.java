package com.danmakulive.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.MimeTypeUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class BroadcastPerConnectionBenchmark {

    private Map<String, ExecutorSubscribableChannel> sessions;
    private Message<byte[]> sourceMessage;
    private ExecutorSubscribableChannel outboundChannel;
    private ThreadPoolTaskExecutor executor;

    @Setup
    public void setup() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.initialize();

        outboundChannel = new ExecutorSubscribableChannel(executor);

        sessions = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            sessions.put("session-" + i, outboundChannel);
        }

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setDestination("/topic/room/abc123");
        accessor.setSubscriptionId("sub-0");
        sourceMessage = MessageBuilder.createMessage(
                "{\"id\":\"uuid-123\",\"roomId\":\"abc123\",\"content\":\"测试弹幕\"}".getBytes(),
                accessor.getMessageHeaders());
    }

    @TearDown
    public void tearDown() {
        executor.shutdown();
    }

    // ---------- 单连接完整操作 ----------

    @Benchmark
    public void perConnectionFull(Blackhole bh) {
        String sessionId = "session-5000";
        String subscriptionId = "sub-0";

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setSubscriptionId(subscriptionId);
        headers.copyHeadersIfAbsent(sourceMessage.getHeaders());

        Message<?> reply = MessageBuilder.createMessage(
                sourceMessage.getPayload(), headers.getMessageHeaders());

        ExecutorSubscribableChannel ch = sessions.get(sessionId);
        if (ch != null) {
            ch.send(reply);
        }
    }

    // ---------- 仅消息构建 ----------

    @Benchmark
    public void messageBuildOnly(Blackhole bh) {
        String sessionId = "session-5000";
        String subscriptionId = "sub-0";

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setSubscriptionId(subscriptionId);
        headers.copyHeadersIfAbsent(sourceMessage.getHeaders());

        Message<?> reply = MessageBuilder.createMessage(
                sourceMessage.getPayload(), headers.getMessageHeaders());
        bh.consume(reply);
    }

    // ---------- 仅 channel send ----------

    @Benchmark
    public void channelSendOnly() {
        outboundChannel.send(sourceMessage);
    }

    // ---------- runner ----------

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(BroadcastPerConnectionBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
