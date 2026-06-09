# Changelogs

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
