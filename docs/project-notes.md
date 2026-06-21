# DanmakuLive 项目笔记

## 1. 弹幕 Pipeline 五阶段处理

弹幕发送的完整处理链，在 `DanmakuPipeline.execute()` 中按序执行：

```
HTTP Request (Tomcat 线程)
  → [Stage 1] RateLimitStage (Redis ZSET Lua 滑动窗口)
  → [Stage 2] SensitiveWordStage (内存 AC 自动机)
  → [Stage 3] MessageBuildStage (构建 DanmakuMessage)
  → [Stage 4] RedisBroadcastStage (Redis Pub/Sub 跨节点广播)
  → [Stage 5] KafkaProduceStage (Kafka 异步持久化)
```

- Stage 1-3 是必须同步完成的（有数据依赖）
- Stage 4 发布到 Redis Pub/Sub → `RedisBroadcastListener` → STOMP → WebSocket 客户端
- Stage 5 发到 Kafka topic `danmaku` → `DanmakuConsumer` 异步写入 MySQL
- 管道内任何一个 Stage 设置 `ctx.error` 后短路，后续 Stage 不执行
- 所有 Stage 实现 `PipelineStage` 接口，通过 `@Order` 控制执行顺序

相关文件：
- `com.danmakulive.danmaku.pipeline.DanmakuPipeline`
- `com.danmakulive.danmaku.pipeline.PipelineContext`
- `com.danmakulive.danmaku.pipeline.stage.*`
- `com.danmakulive.broadcast.RedisBroadcastListener`

---

## 2. 两层 Token 缓存（2026-06-09 新增）

### 架构

```
TokenInterceptor.preHandle()
  → AuthService.resolveToken(token)
    → [L1] Caffeine 本地缓存 (key=token, max 500, TTL 5min)
      ↓ miss
    → [L2] Redis Hash login:token:{token} (TTL 30天，每次访问续期)
```

### 缓存一致性

- **写入**：login/register 时写 Redis（`saveToken`），首次 `resolveToken` 回种 Caffeine
- **驱逐**：`logout()` 同时删 Redis + `@CacheEvict` 清 Caffeine
- **最终一致**：缓存 5min 过期后自动回源 Redis，未主动驱逐的缓存最多不一致 5min

### 设计决策

- 只对 Token 做两层缓存，不扩展到其他数据。Token 满足三个条件：① 每次请求必命中 ② 数据极少变化（30天 TTL） ③ 数据量极小（3个字段）
- 不缓存视频元数据、房间信息等：命中频率远低于 Token，且房间状态变化需要主动失效，复杂度 > 收益
- Caffeine 最大 500 条：按活跃用户数估算，500 条 <1MB 内存，5min TTL 保证不会撑爆

### Caffeine vs GuGoTik go-cache

GuGoTik 的 go-cache 做的是同一件事（进程内存 → Redis → DB 三级回源），但因为 Go 没有 Spring 的 `@Cacheable` 注解，GuGoTik 必须手动写 `GetWithFunc` 回源逻辑。在 Java/Spring 中，`@Cacheable` + Caffeine CacheManager 自动处理回源和回种。

相关文件：
- `com.danmakulive.auth.service.AuthService.resolveToken()` / `logout()`
- `com.danmakulive.common.config.CacheConfig`

---

## 3. 视频弹幕分段缓存（Redis ZSET）

### 架构

```
VideoDanmakuService.getSegments(videoId, from, to)
  → Redis ZSET rangeByScore(key, from, to)
    ↓ 命中 → 直接返回
    ↓ 未命中 → MySQL 查询 [from-buffer, to+buffer] 范围 → 回种 ZSET → 返回
```

### ZSET 结构

- Key: `video:danmaku:{videoId}`
- Score: `playbackTime` (Double，视频播放秒数)
- Value: JSON 序列化的 `DanmakuSegmentDTO`
- TTL: 24h

### 2026-06-09 优化：回填不再全量

之前缓存未命中时查全表 `selectList(videoId)` 回填所有弹幕到 ZSET，弹幕多的视频会导致大量 DB IO 和 Redis 内存消耗。

现在改为只查询 `[from-buffer, to+buffer]` 区间（buffer = 查询范围宽度），只回填请求区间附近的弹幕。如果前端反复拖动进度条，缓存逐步覆盖。

相关文件：
- `com.danmakulive.video.danmaku.service.VideoDanmakuService`

---

## 4. 密度查询 SQL 聚合（2026-06-09 优化）

### 之前

```java
List<VideoDanmaku> all = danmakuMapper.selectList(videoId); // 全量加载到 Java
// Java 内存分段计数
```

### 之后

```java
// SQL: SELECT FLOOR(playback_time / 60) * 60 AS segment, COUNT(*) AS count
//      FROM video_danmaku WHERE video_id = ? GROUP BY FLOOR(playback_time / 60)
List<DensityDTO> result = danmakuMapper.selectDensity(videoId);
```

直接在 DB 侧完成 GROUP BY 聚合，网络只传输聚合结果（几十行），不再传输全量弹幕行。

相关文件：
- `com.danmakulive.video.danmaku.model.mapper.VideoDanmakuMapper.selectDensity()`
- `com.danmakulive.video.danmaku.service.VideoDanmakuService.getDensity()`

---

## 5. 拦截器链认证机制

```
Order 0: TokenInterceptor (所有路径 /**)
  → 从 authorization header 取 token
  → AuthService.resolveToken(token) → Redis Hash → UserDTO
  → UserHolder.saveUser(user) (ThreadLocal)

Order 1: AuthInterceptor (排除白名单路径)
  → 检查 UserHolder.getUser() != null
  → null → 401; 非 null → 放行
```

Token 解析在 Order 0 对所有路径静默执行，包括无需认证的路径（房间列表、弹幕分段等）。这样即使在公开路径上也能拿到用户身份，只是 AuthInterceptor 不拦截。

相关文件：
- `com.danmakulive.auth.config.AuthConfig`
- `com.danmakulive.auth.interceptor.TokenInterceptor`
- `com.danmakulive.auth.interceptor.AuthInterceptor`

---

## 6. 性能测试体系（2026-06-09 从 LivePulse 迁移重建）

### 三层架构

```
Layer 3: Gatling 端到端压测 (danmakulive-gatling/)
         DanmakuSimulation / SpikeSimulation / BroadcastLatencySimulation
         运行: cd danmakulive-gatling && mvn gatling:test -Dusers=500

Layer 2: JUnit 集成性能测试 (com.danmakulive.perf)
         RateLimiterPerfTest / PipelineThroughputTest /
         WebSocketBroadcastLatencyTest / WebSocketCapacityTest
         运行: mvn test -Dtest="com.danmakulive.perf.*"

Layer 1: JMH 微基准 (com.danmakulive.benchmark)
         BroadcastPerConnectionBenchmark (已有) +
         RateLimitStageBenchmark / SensitiveWordFilterBenchmark (新增)
         运行: mvn package && java -jar target/benchmarks.jar
```

### 与原 LivePulse 性能测试的差异

| 维度 | LivePulse | danmakulive | 差异 |
|------|-----------|-------------|------|
| 限流单次延迟 | 1.26ms（3次 Redis 往返） | 预期 ~0.4ms（1次，已合并 Lua） | 显著更快 |
| Pipeline 入口 | 仅测试用 REST 端点 | 已有 POST /api/v1/rooms/{roomId}/danmaku | 无需添加 |
| Gatling | 子模块 livepulse-gatling | 独立项目 danmakulive-gatling/ | 避免父 POM 改为多模块 |
| 数据准备 | 无 | scripts/import_data.py（VTuber 1B + DanmakuTPP） | 新增能力 |

### LivePulse 原始基线数据（对照组）

| 维度 | 指标 | 数值 |
|------|------|------|
| 吞吐 | 峰值 QPS (c50) | 1886 msg/s |
| 吞吐 | P50/P99 (c10) | 6ms / 18ms |
| 广播延迟 | 50 订阅者 P99 | 48ms |
| 广播延迟 | 100 订阅者 P99 | 106ms |
| 连接容量 | 5000 并发 | ~125KB/conn |
| 限流 | 单次 Lua 3次 | 1.26ms |

相关文件：
- `src/test/java/com/danmakulive/benchmark/` (JMH)
- `src/test/java/com/danmakulive/perf/` (JUnit 集成性能测试)
- `danmakulive-gatling/src/test/scala/` (Gatling)
- `scripts/perf/` (辅助脚本)
- `docs/perf/` (性能测试报告)
