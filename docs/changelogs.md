# Changelogs

## 2026-06-09: 性能测试体系迁移重建

### 问题背景

LivePulse 原项目有完善的 5 维度性能测试体系（Pipeline 吞吐 / 广播延迟 / 连接容量 / Kafka 持久化 / 限流器），迁移到 danmakulive 后全部丢失，当前仅有一个 JMH 微基准 `BroadcastPerConnectionBenchmark`。

### 方案设计

按三层重建：JMH 微基准（Layer 1）→ JUnit 集成性能测试（Layer 2）→ Gatling 端到端压测（Layer 3）：

- **Layer 1**: 新增 `RateLimitStageBenchmark`（Redis Lua 执行延迟）+ `SensitiveWordFilterBenchmark`（DFA 过滤性能，6种输入场景）
- **Layer 2**: 新增 `RateLimiterPerfTest`（正确性3项 + 延迟测量）、`PipelineThroughputTest`（单线程 + 多线程 c10/50/100）、`WebSocketBroadcastLatencyTest` + `WebSocketCapacityTest`（独立 main 程序，从 LivePulse 迁移）
- **Layer 3**: 新增 `danmakulive-gatling/` 独立项目，含 `DanmakuSimulation`（稳态）、`SpikeSimulation`（突刺）、`BroadcastLatencySimulation`（广播延迟）三个 STOMP WebSocket 压测场景
- **辅助**: `scripts/perf/create_room.sh`（创建房间+获取 token）、`warmup.sh`（预热）、`run_all.sh`（一键运行全部测试）；`docs/perf/` 报告模板

关键差异：danmakulive 的 RateLimitStage 已将 user/IP/room 三级限流合并到一个 Lua 脚本（一次 Redis 往返），而 LivePulse 是三次往返（1.26ms），预期性能显著提升。

### 文件变更清单

| # | 文件 | 改动说明 |
|---|------|----------|
| 1 | `src/test/java/.../benchmark/RateLimitStageBenchmark.java` | 新增：JMH Redis Lua 执行微基准（freshKey/hotKey/threeKeys） |
| 2 | `src/test/java/.../benchmark/SensitiveWordFilterBenchmark.java` | 新增：JMH DFA 过滤微基准（6种文本/命中率组合） |
| 3 | `src/test/java/.../perf/RateLimiterPerfTest.java` | 新增：限流器正确性 + 1000次平均延迟 |
| 4 | `src/test/java/.../perf/PipelineThroughputTest.java` | 新增：SpringBootTest 全链路单线程 + 多线程吞吐 |
| 5 | `src/test/java/.../perf/WebSocketBroadcastLatencyTest.java` | 新增：N订阅者+M消息端到端延迟（独立main） |
| 6 | `src/test/java/.../perf/WebSocketCapacityTest.java` | 新增：逐步递增连接数测容量上限（独立main） |
| 7 | `danmakulive-gatling/pom.xml` | 新增：Gatling 3.10.5 独立项目 POM |
| 8 | `danmakulive-gatling/src/test/scala/.../DanmakuSimulation.scala` | 新增：稳态负载 STOMP WS 压测 |
| 9 | `danmakulive-gatling/src/test/scala/.../SpikeSimulation.scala` | 新增：突刺压测（基线200→尖峰2000→回落） |
| 10 | `danmakulive-gatling/src/test/scala/.../BroadcastLatencySimulation.scala` | 新增：广播延迟测量 |
| 11 | `danmakulive-gatling/src/test/resources/application.conf` | 新增：Gatling 配置 |
| 12 | `danmakulive-gatling/src/test/resources/logback-test.xml` | 新增：Gatling 日志配置 |
| 13 | `scripts/perf/create_room.sh` | 新增：创建测试房间 + 获取 token |
| 14 | `scripts/perf/warmup.sh` | 新增：预热脚本（ab 或 curl） |
| 15 | `scripts/perf/run_all.sh` | 新增：一键运行全部测试 + 生成报告 |
| 16 | `docs/perf/` | 新增：性能测试报告目录 |
| 17 | `docs/project-notes.md` | 追加 §6 性能测试体系章节 |
| 18 | `docs/changelogs.md` | 本文档 |

### 提交记录

| 时间 | commit hash | commit message |
|------|-------------|----------------|

### 验证步骤

- [x] `mvn test-compile` 通过（新增 6 个 Java 文件全部编译）
- [x] Gatling 项目 `pom.xml` 结构完整
- [ ] `mvn test -Dtest="com.danmakulive.perf.RateLimiterPerfTest"` — 需 docker-compose up（Redis） + dev profile
- [ ] `mvn test -Dtest="com.danmakulive.perf.PipelineThroughputTest" -Dspring.profiles.active=dev` — 需 Redis + Kafka
- [ ] `java com.danmakulive.perf.WebSocketBroadcastLatencyTest 50 100 <roomId> <token>` — 需应用运行
- [ ] `java com.danmakulive.perf.WebSocketCapacityTest <roomId>` — 需应用运行
- [ ] `cd danmakulive-gatling && mvn gatling:test -Dusers=50 -DroomId=<roomId>` — 需应用运行

## 2026-06-09: 性能优化 — 缓存分层 + 全量查询修复 + Gzip

### 问题背景

通过对 GuGoTik（Go 微服务仿抖音）的性能优化模式逐项对照分析，基于 danmakulive 的实际业务流程和代码，识别出以下问题和优化点：

1. **房间列表无分页**：`RoomService.listLiveRooms()` 查全表返回所有 status=1 的房间，无 LIMIT，房间数多了 OOM
2. **密度查询全量加载**：`VideoDanmakuService.getDensity()` 把视频下全部弹幕行加载到 Java 内存再分段计数
3. **缓存回填全量加载**：`VideoDanmakuService.getSegments()` 缓存未命中时查全量弹幕回填 ZSET
4. **Token 解析无本地缓存**：`TokenInterceptor` 每次请求调 Redis HGETALL 解析 Token，是系统最高频的 Redis 操作
5. **HTTP 响应无压缩**：所有 JSON 响应都不压缩

### 方案设计

**P0 缺陷修复**：
- 房间列表：改用 MyBatis-Plus `Page` 分页，默认 page=1, size=20
- 密度查询：新增 Mapper `@Select` 方法，使用 `GROUP BY FLOOR(playback_time/60)` 在 DB 侧完成分段聚合
- 缓存回填：只查询 `[from - buffer, to + buffer]` 区间（buffer = 查询范围宽度），不再全量

**P1 性能优化**：
- Token 两层缓存：Caffeine 本地缓存（最大 500 条、TTL 5min）→ Redis Hash 回源，`logout()` 同步驱逐
- Gzip：`server.compression.enabled=true`，仅压缩 >1KB 的响应

**为何不做 GuGoTik 的其他优化**：
- Bloom Filter：danmakulive 没有"高频查 DB 是否存在"的场景，所有存在性判断都在 Redis 层
- 并发数据聚合（CompletableFuture）：单体架构无跨网络 RPC，业务链天然串行依赖，线程切换开销超过收益
- DB 读写分离：高频读写在 Redis，DB 读写竞争不激烈，现阶过度设计
- 弹幕历史 offset→cursor：P2 锦上添花，大房间才有效果

### 文件变更清单

| # | 文件 | 改动说明 |
|---|------|----------|
| 1 | `pom.xml` | 新增 spring-boot-starter-cache + caffeine 依赖 |
| 2 | `src/main/resources/application.yml` | 新增 spring.cache 配置 + server.compression 配置 |
| 3 | `src/main/java/.../room/service/RoomService.java` | `listLiveRooms()` 加 page/size 参数，改用 selectPage |
| 4 | `src/main/java/.../room/controller/RoomController.java` | `listRooms()` 接收 @RequestParam page/size，返回 IPage |
| 5 | `src/main/java/.../video/danmaku/model/mapper/VideoDanmakuMapper.java` | 新增 selectDensity() @Select 聚合查询 |
| 6 | `src/main/java/.../video/danmaku/service/VideoDanmakuService.java` | getDensity() 改为调 Mapper 聚合；getSegments() 缓存回填改为范围查询 |
| 7 | `src/main/java/.../common/config/CacheConfig.java` (新建) | Caffeine CacheManager 配置，tokenUser 缓存 max 500/TTL 5min |
| 8 | `src/main/java/.../auth/service/AuthService.java` | resolveToken() 加 @Cacheable；logout() 加 @CacheEvict |
| 9 | `src/test/java/.../room/service/RoomServiceTest.java` | 适配 listLiveRooms() 新签名，mock selectPage 替代 selectList |

### 提交记录

| 时间 | commit hash | commit message |
|------|-------------|----------------|
| 2026-06-09 | 75c71ce | 性能优化：P0 全量查询修复 + P1 两层缓存与 Gzip + 文档补全 |

### 验证步骤

- [x] `mvn clean compile` 通过
- [x] `mvn test` 44/45 通过（1 个失败是已有的 KafkaProduceStageTest mock 问题，与本次无关）
- [ ] 启动 docker-compose + mvn spring-boot:run
- [ ] `GET /api/v1/rooms?page=1&size=5` 确认分页带 total
- [ ] 密度查询确认不再全量加载（观察日志）
- [ ] 缓存回填确认不再全量（观察日志）
- [ ] Token 缓存：两次同样 token 的请求，第二次应从 Caffeine 命中
- [ ] Token 登出后请求需认证接口应返回 401
- [ ] curl -H "Accept-Encoding: gzip" 确认 Content-Encoding: gzip
