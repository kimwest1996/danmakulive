package com.danmakulive.perf;

import com.danmakulive.danmaku.service.DanmakuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pipeline end-to-end throughput measurement.
 * Requires Redis + Kafka on localhost (docker-compose up), dev profile.
 *
 * Each call goes through the full pipeline:
 * RateLimitStage → SensitiveWordStage → MessageBuildStage →
 * RedisBroadcastStage → KafkaProduceStage
 */
@SpringBootTest
@ActiveProfiles("dev")
class PipelineThroughputTest {

    @Autowired
    private DanmakuService danmakuService;

    // ==================== single-thread ====================

    @Test
    void singleThreadThroughput() {
        int rounds = 500;
        String roomId = "perf-pipeline-single-" + System.nanoTime();
        AtomicLong totalNanos = new AtomicLong();

        // warmup
        for (int i = 0; i < 100; i++) {
            danmakuService.processDanmaku(roomId, "warmup-" + i, "测试用户",
                    "127.0.0." + (i % 255), "warmup msg " + i);
        }

        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
            danmakuService.processDanmaku(roomId, "perf-user-" + i, "测试用户",
                    "127.0.0." + (i % 255), "perf msg " + i);
            totalNanos.addAndGet(System.nanoTime() - start);
        }

        double avgUs = totalNanos.get() / 1000.0 / rounds;
        double qps = 1_000_000.0 / avgUs;

        System.out.println("=== Pipeline 吞吐 (单线程) ===");
        System.out.println("次数: " + rounds);
        System.out.println("平均延迟: " + String.format("%.1f μs", avgUs));
        System.out.println("估算 QPS: " + String.format("%.0f msg/s", qps));
        System.out.println("总耗时: " + String.format("%.2f ms", totalNanos.get() / 1_000_000.0));
    }

    // ==================== multi-thread ====================

    @Test
    void multiThreadThroughput() throws Exception {
        String roomId = "perf-pipeline-multi-" + System.nanoTime();

        for (int threads : new int[]{10, 50, 100}) {
            int totalRequests = Math.min(threads * 50, 5000);
            int perThread = totalRequests / threads;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicLong totalNanos = new AtomicLong();
            AtomicLong totalPassed = new AtomicLong();
            AtomicLong totalBlocked = new AtomicLong();

            long testStart = System.nanoTime();

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            String userId = "mt-" + threadId + "-" + i;
                            long start = System.nanoTime();
                            String error = danmakuService.processDanmaku(
                                    roomId, userId, "测试用户",
                                    "10.0." + threadId + "." + (i % 255),
                                    "multi perf msg " + i);
                            totalNanos.addAndGet(System.nanoTime() - start);
                            if (error == null) {
                                totalPassed.incrementAndGet();
                            } else {
                                totalBlocked.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            long elapsedMs = (System.nanoTime() - testStart) / 1_000_000;
            int total = totalRequests;
            double avgUs = totalNanos.get() / 1000.0 / total;
            double actualQps = total * 1000.0 / elapsedMs;

            System.out.println("=== Pipeline 吞吐 (c" + threads + ") ===");
            System.out.println("总请求: " + total + "  放行: " + totalPassed + "  拦截: " + totalBlocked);
            System.out.println("总耗时: " + elapsedMs + " ms");
            System.out.println("实际 QPS: " + String.format("%.0f msg/s", actualQps));
            System.out.println("平均延迟: " + String.format("%.1f μs", avgUs));
            System.out.println();
        }
    }
}
