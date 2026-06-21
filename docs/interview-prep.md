# DanmakuLive 面试准备材料

## 模块一：实时弹幕广播与异步落库

> 技术栈：WebSocket/STOMP + Redis Pub/Sub + Kafka + 责任链 Pipeline
> 量化：单机 5000 连接，c100 峰值 2381 QPS，端到端延迟 <50ms

---

### 1. 场景分析

**从最简陋的方案开始推演**：

```
浏览器 → POST /api/sendDanmaku → Controller → INSERT INTO live_danmaku → return 200
观众端：HTTP 轮询 /api/poll → SELECT * WHERE send_time > lastPoll
```

**三个致命缺陷**：

| 问题 | 后果 | 量化 |
|------|------|------|
| HTTP 轮询 | 1 万人每秒轮询 = 1 万请求/秒，99% 返回空数据 | 带宽和 CPU 空转 |
| 同步写 MySQL | 每条弹幕一次磁盘 IO，高峰期 DB 成瓶颈 | 弹幕延迟跟着磁盘抖动 |
| 单节点无法扩展 | 节点 A 发的弹幕，节点 B 上同一房间的观众收不到 | 多节点部署时消息丢失 |

---

### 2. 技术选型推演

#### 决策一：HTTP 轮询 → WebSocket 推送

| 方案 | 优点 | 缺点 | 为什么不选 |
|------|------|------|-----------|
| HTTP 轮询 | 简单，兼容好 | 空转浪费，延迟 = 轮询间隔 | 基准方案，需要替代 |
| HTTP/2 Server Push | 服务端主动推送 | 浏览器支持有限，只能推静态资源 | 协议层面不匹配 |
| SSE | 单向推送，自动重连 | 只能服务端 → 客户端 | 半双工，弹幕需要双向 |
| WebSocket | 全双工，低延迟，单连接复用 | 需要长连接管理 | ✅ 弹幕双向通讯最佳 |

**关键话术**："弹幕是双向通讯场景——用户既要发弹幕又要收弹幕，WebSocket 的全双工是天然匹配。延迟从轮询的 1-2 秒降到网络延迟级别的 <50ms。"

#### 决策二：单节点 → Redis Pub/Sub 跨节点广播

| 维度 | Redis Pub/Sub | Kafka |
|------|--------------|-------|
| 消息模型 | 纯推送，无 offset | Pull 模型，poll + commit offset |
| 延迟 | <1ms | 几十~几百 ms |
| 持久化 | 无（消息即发即忘） | 磁盘持久化 |
| 适用场景 | 实时广播（瞬时消息） | 事件溯源、消息队列 |

**关键话术**："弹幕广播是瞬时消息——丢了就丢了，Kafka 那边还有一份落库的。Pub/Sub 的 <1ms 延迟是 Kafka poll 模型做不到的。但如果弹幕是'必须送达'的支付通知，那必须用 Kafka。选型看消息语义。"

**追问：为什么 PatternTopic 静态订阅而不是动态订阅？**

"动态订阅需要引用计数——房间人数 0→1 时 SUBSCRIBE，1→0 时 UNSUBSCRIBE——需要处理并发安全、引用计数泄漏、窗口期漏消息。静态订阅一行代码，多收几条空房间消息的开销微乎其微。"

#### 决策三：同步写库 → Kafka 异步落库

**关键话术**："推送优先级高于存储——用户发弹幕等的是'别人看到'而不是'存到磁盘'。广播走 Redis Pub/Sub 瞬间完成，Kafka 用 fire-and-forget 发送不等待 ack——写库延迟完全不影响弹幕发送体验。"

---

### 3. 架构设计（全链路）

```
用户发弹幕 (HTTP POST)
  → DanmakuController
    → DanmakuPipeline.execute()
      → [Stage1] RateLimitStage      ← Redis ZSET Lua 滑动窗口，三级限流
      → [Stage2] SensitiveWordStage  ← DFA 字典树，<0.1ms
      → [Stage3] MessageBuildStage   ← 生成 UUID + 时间戳
      → [Stage4] RedisBroadcastStage ← PUBLISH room:123:pubsub → Redis Pub/Sub
      → [Stage5] KafkaProduceStage   ← fire-and-forget 发送 → Kafka
  → 返回 200 (不等待 Kafka ack)

[跨节点广播]
  RedisBroadcastListener (所有节点)
    → 收到 Pub/Sub 消息
    → SimpMessagingTemplate.convertAndSend("/topic/room/123", json)
    → STOMP → WebSocket → 浏览器

[异步落库]
  DanmakuConsumer
    → poll Kafka "danmaku" topic
    → INSERT INTO live_danmaku
```

---

### 4. 核心实现：为什么是责任链不是观察者

**执行模型对比**：

```
责任链（实际用了的）：
  Stage1 → Stage2 → Stage3 → Stage4 → Stage5
     ↓ 失败即停，后续不执行

观察者（如果用了）：
  Stage1 ─┐
  Stage2 ─┤ 同时收到事件，各自独立执行
  Stage3 ─┤ 谁先谁后不确定
  Stage4 ─┤ 某个失败了，其他的继续跑
  Stage5 ─┘
```

| 需求 | 责任链 | 观察者 |
|------|--------|--------|
| 有序执行 | @Order 控制，A→B→C 严格串行 | 通知顺序不确定 |
| 前置依赖（限流不过，敏感词别查） | ctx.error 直接短路 return | 所有观察者都收到事件，各自判断 |
| 数据传递修改（rawContent → filteredContent） | 同一个 PipelineContext 按引用传递 | 观察者间共享可变状态是反模式 |
| 失败即停（限流拒绝 → 返回 429，不广播不落库） | 短路机制天然支持 | 需额外协调机制 |

**具体例子**：用户发带敏感词 + 频率超限的弹幕：
- 责任链：RateLimitStage → 设 error → 短路返回 429，后续 4 个 Stage 都没执行，1 次 Redis Lua 调用结束
- 观察者：5 个 Observer 都收到事件，限流 Observer 设了拒绝状态，敏感词 Observer 还在 DFA 扫描浪费 CPU，广播 Observer 还得检查"前面过了没"

**关键话术**："责任链是审批流——科长批了才到处长，一处卡住全链停。观察者是通知流——公司群发公告，每个人各自处理。弹幕要先过限流再过敏感词，限流没过敏感词根本不该查，这是典型的审批流。"

---

### 5. 线程池替换（面试亮点）

Spring Data Redis 的 `RedisMessageListenerContainer` 默认用 `SimpleAsyncTaskExecutor`——每条消息 `new Thread()`。1000 条弹幕/秒 = 1000 次线程创建/秒，直接把 GC 打爆。

替换为 `ThreadPoolTaskExecutor(16)` 固定线程池。三条独立线程池策略：

| 线程池 | 配置 | 拒绝策略 | 为什么 |
|--------|------|---------|--------|
| Tomcat | 200 | 默认 | HTTP 接入 |
| broadcast | 16/1000 | Discard + AtomicLong 计数器 | 瞬时消息可丢但要可监控 |
| upload | 5/10/200 | CallerRunsPolicy | 用户等待不丢任务 |

---

### 6. WebSocket 连接故障：节点宕机怎么办

**故障瞬间**：节点 A 宕机 → 5000 TCP 连接断开 → 该节点观众失去弹幕流。存活节点 B 不受影响（跨节点架构隔离故障）。

**客户端探测断连**：STOMP 心跳默认 [10000, 10000]，3 次超时 ≈ 30s 确认失联。生产建议 [5000, 5000]，15s 内探测。

**重连策略**：指数退避 + 随机抖动（jitter），防止 5000 客户端同时重连形成 DDoS：
```
attempt 1: 立即 → attempt 2: 1s → attempt 3: 2s → attempt 4: 4s 
→ 最大 30s → 失败后 UI 提示
```

**状态恢复**：重连 → STOMP CONNECT → SUBSCRIBE /topic/room/{id} → WebSocketEventListener.handleSubscribe() → RoomSessionRegistry.register()（已实现）。

**补偿丢失弹幕**：重连期间丢失的弹幕通过 HTTP `GET /api/v1/rooms/{roomId}/danmaku?size=50` 拉取历史补偿。

**完整恢复时间线**：
```
T+0s  : 节点 A 宕机
T+10s : 服务端心跳停止
T+15s : 三次超时，客户端触发重连
T+15s : 负载均衡器踢掉节点 A
T+15.5s: 客户端重连到节点 B → SUBSCRIBE 房间
T+15.6s: 弹幕恢复
全程约 15-20 秒
```

**关键话术**："不搞 session 迁移——WebSocket session 绑 TCP 连接，迁移代价 > 重连代价。弹幕是瞬时消息，重连 + HTTP 补偿拉取比外部消息代理做 session 迁移更务实。"

---

### 7. 量化效果

| 指标 | 数值 |
|------|------|
| 单机 WebSocket 承载 | 5000 连接 |
| HTTP 弹幕链路 c100 峰值 QPS | 2381 |
| 弹幕延迟（端到端） | <50ms |
| Pipeline 全链路单测 | 45 个，纯 Mockito 秒级跑完 |

---

## 模块二：多维滑动窗口限流

> 技术栈：Redis ZSET + Lua 原子脚本 + 本地降级
> 量化：每次判断 ~0.4ms（1 次 EVALSHA），三维递进限流

---

### 1. 场景分析

**版本 0：没有限流**

用户写脚本 1 秒 1000 条弹幕 → 满屏刷屏 → 其他观众体验全毁。

**版本 1：Redis INCR + EXPIRE 固定窗口**

```java
String key = "rl:user:" + userId;
Long count = redis.incr(key);
if (count == 1) redis.expire(key, 1);  // 一秒窗口
if (count > 5) throw new RateLimitException();
```

致命缺陷——**临界突刺**：

```
时间轴: |--- 窗口 A (0s~1s) ---|--- 窗口 B (1s~2s) ---|
请求:            0.9s × 5条      1.1s × 5条

实际:  0.9s~1.1s 这 0.2 秒内穿过了 10 条！
       限流目标是 5条/s，实际 0.2s 内 10 条——限流形同虚设
```

> "固定窗口的致命缺陷是窗口边界。用户在窗口 A 最后 0.1 秒发满 5 条，窗口 B 前 0.1 秒又能发 5 条——实际 0.2 秒内通过了 10 条。这个现象叫临界突刺。"

---

### 2. 技术选型推演

#### 决策一：固定窗口 → 滑动窗口（ZSET）

| 实现方式 | 原理 | 优点 | 缺点 | 弹幕场景 |
|----------|------|------|------|---------|
| 滑动日志（ZSET） | 记录每条请求时间戳，统计窗口内数量 | 精确到毫秒，无窗口边界 | ZSET 内存稍大 | ✅ 需要精确限制 |
| 滑动窗口计数器 | 1s 窗口切 N 个小格子，格子各自 INCR | 内存小 | 精度 = 窗口/N，仍有误差 | ❌ 精度不够 |
| 令牌桶 | 固定速率放入令牌，请求消费令牌 | 允许突发 | 需要定时器 | ❌ 弹幕不允许突发 |
| 漏桶 | 固定速率流出 | 平滑输出 | 不能处理突发 | ❌ 过于严格 |

> "弹幕场景需要精确限流——用户 5 条/秒就是 5 条，不能多也不能少。滑动日志以当前时刻为基准往回看 1 秒，精确统计真实请求数，没有窗口概念。令牌桶允许突发——用户可能 0.1 秒发 5 条——对弹幕来说是不想要的。"

#### 决策二：Lua 脚本 vs MULTI/EXEC 事务

> "MULTI/EXEC 不能做条件判断（ZCARD > limit 才 ZADD），必须先拿到结果在客户端判断，再决定要不要写入——三次 Redis 往返 + 非原子间隙。Lua 脚本五步操作一次 EVALSHA 完成：原子性 + 条件逻辑 + 单次网络往返，延迟从 1.26ms 降到 ~0.4ms。"

#### 决策三：为什么是三维递进限流

```
用户级: rl:user:{userId}  → 5条/s   → 防止单用户刷屏
IP级:   rl:ip:{clientIp}  → 20条/s  → 防止脚本多账号绕过用户级
房间级: rl:room:{roomId}  → 1000条/s → 防止热门房间广播线程池被打爆
```

> "三层是递进防线。用户级拦截单用户恶意行为，IP 级防止注册多个账号绕过用户级，房间级是系统保护。短路判断：用户超限就不再检查 IP 和房间，节省 Redis 调用。"

---

### 3. 核心实现

#### Lua 脚本

```lua
-- KEYS[1] = rl:user:{id}
-- ARGV[1] = 当前时间戳(ms)
-- ARGV[2] = 窗口大小(ms) = 1000
-- ARGV[3] = 限流阈值 = 5
-- ARGV[4] = key TTL = 2s

redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1] - ARGV[2])  -- 清理1秒前过期
local count = redis.call('ZCARD', KEYS[1])                        -- 当前窗口内计数
if count >= tonumber(ARGV[3]) then return 0 end                   -- 超限拒绝
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1] .. ':' .. math.random())  -- 追加
redis.call('EXPIRE', KEYS[1], ARGV[4])                            -- 冷key自动清理
return 1
```

**追问：ZADD 的 member 为什么是 `timestamp .. ':' .. math.random()`？**

> "ZSET member 不能重复。如果两个请求在同一毫秒内到达，纯时间戳会让第二个 ZADD 覆盖第一个——少记一次请求。加上随机数保证唯一性，计数准确。"

**追问：EXPIRE 为什么 2 秒而不是 1 秒？**

> "给 1 秒的余量，确保窗口内必然不过期。窗口是 1 秒，如果 EXPIRE 也是 1 秒，用户恰好在第 1 秒整发弹幕可能刚好碰上 key 过期。2 秒保证安全余量，且窗口外很快回收，内存影响忽略不计。"

#### 三级短路判断

```java
if (!rateLimiter.tryAcquire("user", userId, 5)) {
    ctx.setError("发送频率过快"); return;  // 用户超限，不再查 IP 和房间
}
if (!rateLimiter.tryAcquire("ip", clientIp, 20)) {
    ctx.setError("IP频率超限"); return;
}
if (!rateLimiter.tryAcquire("room", roomId, 1000)) {
    ctx.setError("房间弹幕过载"); return;
}
```

#### Redis 故障降级

> "catch 到 Redis 异常后，`AtomicBoolean degraded` 标记降级，切换为本地 `ConcurrentLinkedDeque` 滑动窗口——`synchronized` 块内 peekFirst 清理过期 + size 判断。降级到本地无法跨节点协调，但比完全不限流要好。Redis 恢复后下次请求自动切回。"

**追问：降级到本地有什么问题？**

> "两个问题：① 无法跨节点协调——用户在节点 A 发了 5 条，节点 B 不知道（可通过 Nginx ip_hash 路由缓解）；② 本地数据不持久，重启丢失。但在降级场景下可接受——降级的目的是'比没有强'，不是'和 Redis 一样好'。"

---

### 4. 面试追问预判

**Q: 为什么不直接用 Sentinel？**

> "Spring Cloud Alibaba Sentinel 整套依赖太重，和项目'单体优先、不引入 Spring Cloud'的原则冲突。20 行 Lua 脚本实现相同效果，无额外依赖。但如果是公司项目且已有 Sentinel 基础设施，直接用 Sentinel 是合理选择——了解原理 + 务实选型。"

**Q: 滑动窗口 vs 令牌桶，什么场景用哪个？**

> "弹幕用滑动窗口——需要精确限制'过去 1 秒最多 5 条'，不允许突发。API 网关用令牌桶——允许一定突发应对瞬时流量，平滑长期速率。判断标准：看业务是否允许突发。"

**Q: 100 万用户同时在线，ZSET 内存扛得住吗？**

> "ZSET 5条/s × 1s 窗口 × (8B score + ~20B member) ≈ 140B/用户。100 万活跃 = 140MB，在 Redis 单节点范围。EXPIRE 2s 保证冷 key 快速回收，实际内存取决于活跃用户数而非总用户数。量级更大可 Redis Cluster 按 userId hash 分片。"

---

## 模块三：视频弹幕多级缓存 + Write-Behind 写入

> 技术栈：Caffeine L1 + Redis ZSET L2 + 逻辑过期 + 互斥锁
> 量化：QPS 7947→13330（+67%），P99 868ms→55ms（-93%）

---

### 1. 场景分析：直播弹幕 vs 视频弹幕

| 维度 | 直播弹幕 | 视频弹幕 |
|------|---------|---------|
| 时间基准 | 挂钟时间（实时） | 视频进度时间（可拖拽） |
| 读取模式 | WebSocket 订阅推送（实时流） | HTTP 按范围查询（用户拖到哪看到哪） |
| 查询特征 | 不需要历史查询 | 反复拖拽，同一视频反复看 |
| 写入模式 | 纯推送，不缓存 | Write-Behind：同步写 Redis ZSET 保证立即可见 |
| 优化方向 | 推送延迟 | 查询 QPS、缓存命中率 |

---

### 2. 写入路径：Write-Behind 模式

#### 直播 vs 视频写入差异

```
直播弹幕写入（纯推送，不缓存）：
  sendDanmaku(roomId, content)
    → Pipeline:
        Stage4: RedisBroadcastStage → PUBLISH room:123:pubsub  (实时广播)
        Stage5: KafkaProduceStage   → fire-and-forget Kafka    (异步落库)
    → return 200  ← 链路结束，不做额外缓存

视频弹幕写入（推送 + 缓存双写）：
  sendDanmaku(videoId, content, playbackTime)
    → Pipeline:
        Stage4: RedisBroadcastStage → PUBLISH video:123:pubsub (实时广播)
        Stage5: KafkaProduceStage   → fire-and-forget Kafka    (异步落库)
    → Pipeline 返回，无 error：
        redis.opsForZSet().add("video:danmaku:{id}", json, playbackTime) ← 同步写 ZSET
        refreshLogicExpire(videoId)  ← 刷新逻辑过期时间
    → return 200
```

> "直播弹幕通过 WebSocket 实时推送，在线观众立即收到，不需要查缓存。视频弹幕是'点播'——用户随时拖进度条，随时查某个时间段的弹幕。如果等 Kafka consumer 落库再从 DB 回填缓存，刚发的弹幕几秒内查不到。同步写 Redis ZSET 保证写入者立即可见。"

#### Write-Behind vs Write-Through vs Write-Around

| 模式 | 做法 | 延迟 | 一致性 | 项目中的应用 |
|------|------|------|--------|------------|
| Write-Through | 同步写缓存 + 同步写 DB | 高（等 DB） | 强一致 | ❌ 弹幕不需要等磁盘 |
| Write-Behind | 同步写缓存 + 异步写 DB | 低（不等 DB） | 最终一致 | ✅ 视频弹幕用的这个 |
| Write-Around | 只写 DB，读时回填 | 低（写路径） | 取决于 TTL | ❌ 新弹幕立马查不到 |

> "视频弹幕是 Write-Behind：Pipeline 返回前同步写 Redis ZSET（保证立即可见），Kafka 异步落库（解耦磁盘 IO）。直播弹幕不缓存，不需要 Write-Behind。Write-Behind 的风险是 Redis 数据丢失——但 Kafka 里还有，consumer 恢复后会写到 DB，对弹幕可接受。"

#### 同一套 Pipeline 如何区分两种场景

```java
// PipelineContext.scene 字段在三个地方路由：

// 1. RateLimitStage：视频跳过用户级限流
if (ctx.isVideo()) { /* 只做 IP + 视频级，不做用户级 5/s */ }

// 2. RedisBroadcastStage：channel 前缀不同
channel = ctx.isVideo() ? "video:{id}:pubsub" : "room:{id}:pubsub";

// 3. VideoDanmakuService：只有视频场景追加 ZSET 写入
if (!ctx.hasError()) redis.opsForZSet().add(key, json, playbackTime);
```

> "Pipeline 五个 Stage 是公共逻辑，`PipelineContext.scene` 让每个 Stage 在关键分支上区分行为。新增场景加枚举值 + 分支即可——责任链 + 策略的混合用法。"

---

### 3. 读路径：三层查缓存链路

```
getSegments(videoId, from=120, to=180)
  │
  ├─ L1: Caffeine.get(videoId:2)     ← segmentIdx = 120/60 = 2，60s分段
  │    └─ hit → 直接返回（纯内存，0 网络调用）
  │
  ├─ L2: Redis ZRANGEBYSCORE video:danmaku:{id} 120 180
  │    │
  │    ├─ 命中 & 未逻辑过期 → 回种 Caffeine → 返回
  │    │
  │    ├─ 命中 & 逻辑过期 & 抢锁成功   → 返回旧数据 + CompletableFuture 异步重建
  │    │
  │    └─ 命中 & 逻辑过期 & 抢锁失败   → 返回旧数据（别人在重建了）
  │
  └─ L3: Redis 未命中（真 miss）
       └─ 互斥锁循环
            ├─ tryAcquireLock 成功 → double check → loadFromDb → 回填 → 释放锁
            └─ tryAcquireLock 失败 → sleep(50ms) → 重试循环
```

---

### 4. 两个防缓存崩溃机制深度拆解

#### 机制一：逻辑过期（防击穿）

**为什么不直接删 key？**

> "删 key 导致：缓存失效后第一个请求穿透到 DB，如果恰好 100 个并发请求同时打到这个失效 key，全部穿透。逻辑过期是'旧数据继续服务 + 异步刷新'——数据逻辑上过期了但物理还在，读请求不受影响。只有拿到锁的那个线程去重建，其他 99 个请求立即返回旧数据。"

```java
// 命中 ZSET 后检查逻辑过期
if (isLogicExpired(videoId) && tryAcquireLock(videoId)) {
    // 只有抢到锁的线程异步重建，其他线程走旧数据
    CompletableFuture.runAsync(() -> {
        backfillFromDb(videoId, key, from, to);
        refreshLogicExpire(videoId);  // 更新 meta key 过期时间
    });
}
// 旧数据立即返回，不阻塞用户
return cached;
```

> "逻辑过期 24h（meta key 存 `now + 24h`），物理过期由 ZSET 自身 TTL 控制。两者分离：逻辑过期频繁触发刷新但数据一直在，物理过期才是真删除。逻辑过期比物理过期短，保证数据在被物理删除前有机会刷新。"

#### 机制二：互斥锁 + 轮询（防穿透）

**为什么缓存 miss 不用直接查库？**

> "缓存 miss 时大量并发请求同时查 DB 回填缓存——这就是缓存穿透（不是恶意不存在的 key，而是 key 恰好过期）。互斥锁保证只有一个线程查库，其他线程 sleep 50ms 后重试拿缓存。"

```java
while (true) {
    if (tryAcquireLock(videoId)) {       // SETNX，30s TTL
        try {
            // double check：前一个线程可能已经回填好了
            Set<String> retry = redis.opsForZSet().rangeByScore(key, from, to);
            if (retry != null && !retry.isEmpty()) return ...;
            
            // 真的没数据，查 DB 回填
            List<DanmakuSegmentDTO> result = loadFromDb(videoId, key, from, to);
            refreshLogicExpire(videoId);
            return result;
        } finally {
            redis.delete(lockKey);       // 释放锁
        }
    }
    Thread.sleep(50);  // 没抢到锁，休眠重试
}
```

**追问：为什么 `while(true)` 而不是只重试一次？**

> "拿到锁的线程查 DB + 回填可能需要 200ms，只 sleep 一次 50ms 醒来时 DB 还没回填完又 miss。while(true) 保证最终能拿到缓存。SETNX 有 30s TTL，锁不会永久不释放。可加最大重试次数（20 次 = 1s）然后降级直接查库作为兜底。"

#### 两种机制的适用场景区分

| 机制 | 场景 | 触发条件 | 用户感知 |
|------|------|---------|---------|
| 逻辑过期 | 缓存有旧数据，只是逻辑上到期了 | isLogicExpired() = true | 无感知（旧数据先用着） |
| 互斥锁 | 缓存完全没有数据 | ZRANGEBYSCORE 返回空 | 等到查库完成才返回 |

---

### 5. 缓存粒度设计：为什么是 ZSET + 60s 分段

**决策一：Redis 为什么用 ZSET 而不是 String？**

> "String 不支持范围查询——用户要 120s~180s 弹幕，只能把整个视频弹幕都拉出来再过滤。ZSET 的 ZRANGEBYSCORE 天然按播放进度返回目标区间数据。50 万条弹幕的视频，只返回那一段的几十条——网络传输从几十 MB 降到几 KB。"

**决策二：Caffeine 为什么按 60 秒分段？**

> "用户拖拽进度条往往在同一个 60 秒片段内反复微调——比如看高燃片段反复前后拖。Caffeine key = videoId:segmentIdx（segmentIdx = from/60），同一片段命中本地缓存，0 网络开销。60 秒是权衡：太细（10s）分段太多碎片化，太粗（300s）每次传输数据量大。"

---

### 6. 密度查询优化（SQL 聚合代替 Java 内存聚合）

**之前**：全量查 MySQL → Java 内存分组计数 → 几十万条对象 → GC 抖动

**之后**：
```sql
SELECT FLOOR(playback_time / 60) * 60 AS segment, COUNT(*) AS count
FROM video_danmaku WHERE video_id = ?
GROUP BY FLOOR(playback_time / 60) ORDER BY segment
```

> "50 万条弹幕的视频，之前全量查出 50 万行，网络传输几十 MB。SQL GROUP BY 后只传聚合结果（2 小时视频 = 120 行），数据量从几十 MB 降到几 KB。"

---

### 7. 量化效果

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| QPS | 7947 | 13330 | +67% |
| P99 延迟 | 868ms | 55ms | -93% |

> "QPS +67% 主要是 Redis ZSET 范围查询替代全量 MySQL。P99 -93% 是 Caffeine 本地命中 + Redis 网络调用替代了全表扫描 + 网络传输几十 MB 的那个 P99 慢请求。"

---

### 8. 面试追问预判

**Q: Caffeine 为什么 2 分钟不是更长？**

> "① 新弹幕最多 2 分钟被看到，可接受；② 5000 分段 × 列表数据，内存可控；③ 视频场景用户不太会 2 分钟内反复拖同一个分段，2 分钟足够覆盖大部分重复拖拽。如果内存充裕可调到 5-10 分钟，但收益递减。"

**Q: 如果视频长达 5 小时，ZSET 内存多大？**

> "5 小时 × 假定每分钟 100 条弹幕 = 3 万条。每条 JSON ~200B → ~6MB ZSET。百万条级别才需要担心，此时可加 ZRANGEBYSCORE LIMIT 分页限制单次返回量。"

**Q: 怎么应对热点视频的缓存压力？**

> "本项目的单节点架构，Caffeine L1 已经吸收了大多数重复拖拽的请求。多节点部署时可对同一个分段 Caffeine key 做 local cache（每个节点各自缓存），配合一致性哈希路由让同一视频请求尽量落在相同节点，提高 L1 命中率。"

---

## 模块四：分块上传与秒传

> 技术栈：MinIO Presigned URL + SHA-256 秒传 + composeObject 服务端合并
> 量化：服务端不沾文件字节，1GB 上传期间 HTTP 线程占用 < 100ms

---

### 1. 场景分析

**最简陋方案**：前端 POST 整个文件，后端 MultipartFile 接收字节，再转存 MinIO。

```
浏览器 → POST /api/upload (整个 1GB) → 后端接收 byte[]
  → 内存攒 1GB → MinIO putObject → return 200
```

三个致命问题：

| 问题 | 后果 | 量化 |
|------|------|------|
| 服务端沾文件字节 | 1GB 上传 = 1GB 应用内存 | 其他请求被 GC 卡住 |
| HTTP 线程被占 | 1GB 上传可能几分钟 | 200 Tomcat 线程很快耗尽 |
| 重复上传同一文件 | 同一视频被多用户上传 | 存储和带宽双重浪费 |

---

### 2. 技术选型推演

#### 决策一：服务端接收字节 → Presigned URL 直传

| 方案 | 服务端内存 | 服务端带宽 | 上传瓶颈 |
|------|-----------|-----------|---------|
| MultipartFile 接收 | O(文件大小) | O(文件大小) | Tomcat 线程数 |
| Presigned URL 直传 | O(1)（只生成 URL） | O(1)（不沾字节） | MinIO 带宽 |

> "Presigned URL 是 MinIO/S3 的标准能力——服务端用 accessKey+secretKey 签名一个临时 URL，客户端拿到后直传 MinIO。服务端全程不沾文件字节，只做协调：生成 URL + 合并分块。1GB 上传期间，后端 HTTP 线程只被占几十毫秒。"

#### 决策二：整体上传 → 分块上传（5MB）

> "三个原因。① S3/MinIO 的 composeObject 要求每块 ≥ 5MB（协议硬限制）。② 断点续传——1GB 传到第 180 块网络断了，只需重传该块。③ 并行上传——浏览器同时 PUT 多个分块提升速度。1GB = 200 块（5MB），在 composeObject 上限 1000 块以内。"

#### 决策三：SHA-256 秒传

| 哈希算法 | 碰撞风险 | 适用场景 |
|----------|---------|---------|
| MD5 | 已知碰撞漏洞 | ❌ 文件去重不可靠 |
| SHA-256 | 碰撞概率可忽略 | ✅ 文件指纹可靠 |

> "MD5 已有实际碰撞攻击——两个不同文件算出相同 MD5，会导致秒传拿到别人的视频。SHA-256 碰撞在密码学上不可行。"

---

### 3. 核心实现：三步上传架构

```
═══════════════════════════════════════════════════════
阶段 1: Check（秒传检测）
═══════════════════════════════════════════════════════
客户端计算文件 SHA-256
  → GET /api/v1/video/upload/check?hash={sha256}
    → SELECT * FROM video_upload WHERE file_hash = ?
      ├─ 无记录     → {exists: false}           → 继续阶段 2
      ├─ status=0   → {uploading: true}          → "有人正在上传"
      └─ status=1   → {exists: true, videoId}    → 秒传！跳过阶段 2+3
         ↑ uk_file_hash 唯一索引保证并发安全

═══════════════════════════════════════════════════════
阶段 2: Init（生成 Presigned URL）
═══════════════════════════════════════════════════════
客户端 POST /api/v1/video/upload/init
  → 双重校验文件哈希不存在
  → for i in 0..chunkCount:
      minio.getPresignedObjectUrl(PUT, bucket, "{hash}/{i}", 15min)
  → INSERT INTO video_upload (status=0)  ← 标记上传中
  → return [presignedUrl0, presignedUrl1, ...]

客户端拿到 URL 后直传 MinIO（不经过后端）：
  → PUT minio:9000/bucket/{hash}/0
  → PUT minio:9000/bucket/{hash}/1
  → ...

═══════════════════════════════════════════════════════
阶段 3: Merge（服务端合并）
═══════════════════════════════════════════════════════
客户端所有分块 PUT 完成
  → POST /api/v1/video/upload/{uploadId}/merge
    → 检查 upload status=0
    → minio.composeObject(
        sources = [{hash}/0, {hash}/1, ...],
        object  = "{hash}/merged.mp4"
      )  ← MinIO 服务端内部完成，应用层不沾数据
    → INSERT INTO video (objectKey = "{hash}/merged.mp4")
    → UPDATE video_upload SET status=1, video_id=?
    → CompletableFuture.runAsync(() -> deleteChunks())
      ← 异步删分块，不阻塞返回
    → return {videoId, objectKey}
```

---

### 4. 核心设计细节

#### Presigned URL 安全边界

> "URL 有效期 15 分钟，过期失效。URL 中不含 accessKey/secretKey——签名是 HMAC 计算结果，不可逆推密钥。只生成 PUT 权限——只能上传该分块位置，不能读删其他文件。"

#### composeObject 为什么不在应用层做

> "应用层合并 = 下载所有分块到服务端内存 + 拼接 + 再上传。1GB 文件 = 1GB 应用内存。composeObject 在 MinIO 服务端完成，应用层只发一条 API，内存 O(1)。"

#### 并发上传冲突处理

> "`uk_file_hash` 唯一索引在 DB 层保证串行。第一个请求 INSERT 成功（status=0），第二个请求撞索引失败。Init 阶段也先 SELECT 检查——查到 status=0 返回'有人正在上传'。简单可靠。"

#### 分块清理双重保障

> "merge 成功后 `CompletableFuture.runAsync` fire-and-forget 即时删，不阻塞返回。`@Scheduled(fixedRate=30min)` 定时扫描 status=1 的 upload 兜底清理残留。"

---

### 5. 线程池策略：CallerRunsPolicy + 自然背压

| 线程池 | 配置 | 拒绝策略 | 为什么 |
|--------|------|---------|--------|
| broadcast | 16/1000 | Discard + 计数器 | 瞬时消息可丢 |
| upload | 5/10/200 | CallerRunsPolicy | 用户等待不丢 |

> "上传是用户等待型操作（盯着进度条），任务不能丢。CallerRunsPolicy 形成自然背压：队满 → Tomcat 线程自己执行 → HTTP 线程被占 → accept 队列积压 → TCP RST。用户看到的是'稍慢但成功'或'连接拒绝可重试'，不是'超时无响应'。"

---

### 6. 面试追问预判

**Q: 上传完分块不调 merge 怎么办？**

> "分块按 `{hash}/` 前缀存储在 MinIO，不 merge 就是孤立对象。可加定时任务扫描 status=0 且超过 24 小时的 upload 清理对应分块。当前待完善。"

**Q: merge 中途服务崩溃？**

> "upload 表 status 仍为 0。MinIO composeObject 是原子操作——要么成功创建合并对象，要么不创建。恢复后客户端重调 merge 即可——第一步检查 status=0，幂等安全。"

**Q: presigned URL 15 分钟够吗？**

> "1GB = 200 块。家庭上行 30Mbps 每块 ~1.3s，并行 10 路 ≈ 26s——15 分钟绰绰有余。极慢网络（<1Mbps）可能不够，需 URL 续期能力，当前暂未支持。"

**Q: 秒传如何防碰撞攻击？**

> "SHA-256 抗碰撞在密码学上已验证，找到两个不同文件相同哈希在计算上不可行。即使有人故意碰撞，收益是'帮别人免费存文件'——在弹幕场景不构成安全威胁。安全敏感场景可加一层元数据校验（时长、分辨率）。"

**Q: 为什么用 MySQL 而不用 Redis 记录上传状态？**

> "upload 记录 merge 后转为 video 正式记录，需要持久化。`uk_file_hash` 唯一索引依赖 DB 约束——MySQL 是天生的选择。Redis 在上传场景中只适合做去重缓存，但不是权威数据源。"

---

## 分布式系统视角：从整体架构上学到了什么

> 面试官追问完细节后的拔高问题——考察的是系统设计判断力，不是技术名词堆砌。

---

### 1. 节点通信：三种通信模式各司其职

```
                    ┌──────────────┐
                    │    Redis     │
                    │  Pub/Sub     │  ← 广播模式（一对多，纯推送）
                    └──┬───┬───┬──┘
                       │   │   │
              ┌────────┘   │   └────────┐
              ▼            ▼            ▼
         ┌─────────┐ ┌─────────┐ ┌─────────┐
         │ Node A  │ │ Node B  │ │ Node C  │
         └────┬────┘ └────┬────┘ └────┬────┘
              │           │           │
              └───────────┼───────────┘
                          │
                    ┌─────▼──────┐
                    │   Kafka    │
                    │ (danmaku)  │  ← 消息队列模式（消费者组，pull）
                    └────────────┘
```

| 模式 | 在项目里 | 通信特征 | 可靠性语义 |
|------|---------|---------|-----------|
| Pub/Sub 广播 | RedisBroadcastStage → 跨节点推送弹幕 | 一对多，纯推，无确认 | fire-and-forget，允许丢 |
| 消息队列 | KafkaProduceStage → 消费者落库 | 一对多，pull + offset | at-least-once，不允许丢 |
| 请求-响应 | HTTP POST 发弹幕 → return 200 | 一对一，同步 | 客户端确认即成功 |

**为什么这个系统不需要服务发现？**

> "因为节点之间不直接通信——节点 A 不知道节点 B 的存在。所有跨节点交互都通过 Redis 和 Kafka 两个中间件中转。这是一种**代理式解耦**：节点只需知道 Redis/Kafka 的地址，不用维护集群拓扑。代价是中间件成了单点——Redis 挂了广播全断，但这也是为什么有降级机制。"

---

### 2. 一致性模型：同系统内三种一致性并存

分布式系统不会只有一种一致性——不同数据有不同需求，强行统一是过度设计。

```
强一致 ──────────────────────────── 弱一致
   │                                    │
 MySQL                    Kafka         Redis       Caffeine
 ACID事务               异步落库       ZSET缓存      本地缓存
 uk_file_hash            秒级延迟      逻辑过期24h    2min TTL
 (上传去重)            (弹幕历史)     (视频弹幕)    (视频分段)
```

| 数据 | 一致性要求 | 实现 | 违反会怎样 | 决策理由 |
|------|-----------|------|-----------|---------|
| 上传去重 | 强一致 | MySQL 唯一索引 | 秒传拿到别人的视频 | 不可接受 |
| 弹幕落库 | 最终一致 | Kafka 异步消费 | 历史暂时查不到 | 可接受 |
| 视频弹幕缓存 | 最终一致 | Redis ZSET + 逻辑过期 | 少看几条新弹幕 | 可接受 |
| 用户 Token | 最终一致 | Redis + Caffeine 5min TTL | Token 吊销 5min 内有效 | 可接受 |

> "核心原则：**用户等待型操作走强一致，用户不等待的走最终一致。** 和黑马点评里优惠券秒杀的一致性选择同一个思路——下单走强一致（不能超卖），库存显示走最终一致（缓存延迟几秒没人看出来）。"

---

### 3. 容错设计：不是"不出错"而是"出了错怎么兜"

#### 故障传播链分析

```
故障源：Redis 宕机
  ├─ 弹幕广播：Pipeline 短路 → 弹幕发不出 → ❌ 致命
  ├─ 限流：catch 异常 → 降级本地窗口 → ✅ 兜底（精度下降但不裸奔）
  ├─ Token 解析：Caffeine miss → 公开 API 仍可用，认证 API 401 → ⚠️ 部分可用
  └─ Pub/Sub 订阅：连接断开 → 重连 → ✅ 自愈

故障源：Kafka 宕机
  ├─ 弹幕落库：whenComplete 回调 warn → 不影响 200 → ✅ 广播不受影响
  └─ 回放 ETL：消息发不出 → @Scheduled 30min 扫描兜底 → ✅ 最终一致

故障源：MySQL 宕机
  ├─ 上传/历史查询：直接报错 → ❌ 致命（但影响面小）
  ├─ 弹幕广播：不依赖 MySQL → ✅ 不受影响
  └─ 缓存未命中：loadFromDb 报错 → 返回空 → ⚠️ 降级
```

> "弹幕实时广播路径（Stage 4）和持久化路径（Stage 5）是故障隔离的——Kafka 挂了广播照常，MySQL 挂了广播也照常。弹幕系统的核心价值是'实时看到'不是'永久存储'。这和支付系统优先级完全相反——支付必须保证落库成功才能返回成功。"

#### 降级策略的"够用就好"原则

| 降级点 | 降级到什么 | 损失什么 | 为什么够用 |
|--------|-----------|---------|-----------|
| Redis 限流 → 本地窗口 | ConcurrentLinkedDeque | 跨节点协调 | ip_hash 让同用户落同节点 |
| 缓存过期 → 旧数据 | 逻辑过期继续服务 | 数据一致性 | 弹幕不是支付订单 |
| 互斥锁死等 → 查库 | sleep 50ms 轮询 | 响应延迟 | SETNX 30s TTL 防永久阻塞 |
| merge 后删分块 → 定时 | @Scheduled 30min | 临时多存分块 | MinIO 存储成本低 |

---

### 4. 扩展性：从单节点到多节点

#### 已解决的问题

```
✅ 跨节点广播：Redis Pub/Sub，所有节点订阅同一 pattern，各自推送给自己的客户端
✅ 弹幕落库：Kafka 消费组天然多节点，同组自动分区
✅ 用户认证：Token 存 Redis，任何节点都能解析
✅ 视频弹幕缓存：Redis ZSET 共享，任何节点都能读
```

#### 尚未解决（面试中可坦诚讨论）

```
❓ Caffeine L1 命中率：多节点下用户可能每次路由到不同节点，
   配合一致性哈希路由可让同视频请求尽量落同节点，提升 L1 命中

❓ Redis Pub/Sub 单点：单节点 Redis 挂了广播全停，
   用 Redis Sentinel 做主从自动切换；降级时 HTTP 轮询兜底

❓ SimpleBroker 内存上限：单节点 10000 连接，
   但无状态架构天然支持加节点水平扩展
```

---

### 5. 设计取舍速查表：每个选择都有代价

| 做了这个选择 | 得到了什么 | 放弃了什么 | 什么时候会后悔 |
|-------------|-----------|-----------|-------------|
| Redis Pub/Sub 而不是 Kafka 做广播 | <1ms 延迟 | 消息可靠性 | 需要回溯消息时 |
| 静态 PatternTopic | 零生命周期管理 | 多收无用消息 | 房间数超大时 |
| ZSET 滑动窗口而不是令牌桶 | 精准限流 | 不允许突发 | 秒杀类需瞬时突发时 |
| Lua 脚本而不是 Redis 事务 | 原子 + 条件判断 | 调试复杂 | 限流逻辑频繁变动时 |
| Write-Behind 而不是 Write-Through | 写入低延迟 | 缓存/DB 短暂不一致 | 缓存挂数据全丢时 |
| 逻辑过期而不是物理过期 | 不阻塞读请求 | 旧数据多活一会儿 | 写后必须立刻读到时 |
| CallerRuns 而不是 Discard | 不丢任务 | 线程可能被占 | 上传量翻 10 倍时 |
| BCrypt 而不是 Spring Security | 轻量简单 | 没 OAuth2/RBAC | 需第三方登录时 |
| Presigned URL 而不是服务端转发 | 服务端零带宽 | 不控上传内容 | 需上传时做内容审核时 |

---

### 6. 一句话总结（面试收尾用）

> "这个项目是一个**单体优先的伪分布式系统**——单体部署能工作，多节点利用 Redis Pub/Sub 实现无状态横向扩展。设计上刻意分离实时路径和持久化路径——广播走 <1ms 的 Pub/Sub 允许丢消息，落库走 Kafka 保证最终一致。容错遵循'够用就好'——关键路径有降级，非关键路径有定时兜底，不做过度防御。本质上是用'理解分布式原理 + 务实单体实现'来体现架构判断力，而不是堆中间件。"