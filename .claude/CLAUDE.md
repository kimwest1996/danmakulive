# DanmakuLive — Claude Code 开发指令

## 项目定位

高并发直播弹幕实时互动后端，求职导向项目。

对外展示仓库 `github.com/caohanqing/danmakulive`，开发文档在 `.claude/notes/`（gitignore，不公开）。

## 技术栈

- Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5
- Spring WebSocket + STOMP（跨节点广播）
- Kafka 3.x（弹幕持久化）
- Redis 7（Pub/Sub 广播、限流、Token 存储）
- MySQL 8（弹幕归档、用户信息）
- Spring Security Crypto（BCrypt 密码，无 Spring Security Filter）

## 包结构

领域 + 内部分层：controller → service → model(entity/dto/mapper) → config

完整包树见 `.claude/notes/package-structure.md`

## 基础设施规范

### 统一响应
- 成功：`Result.success(data)`，code="0"
- 失败：抛 `ClientException`（400 系列）或 `ServiceException`（500 系列）
- GlobalExceptionHandler 自动转换异常为 Result.failure

### 数据访问
- Entity 继承 `BaseDO`（自动填充 createTime/updateTime）
- Mapper 继承 `BaseMapper<Entity>`（MyBatis-Plus）
- ID 统一使用 UUID 字符串（`IdType.ASSIGN_UUID`）

### 认证
- TokenInterceptor（order=0）：拦截所有，从 Redis Hash 拿用户
- AuthInterceptor（order=1）：拦截需认证路径，无用户返回 401
- 用户身份通过 `UserHolder.getUser()` 获取

## 命令

```bash
mvn spring-boot:run       # 启动应用（需先 docker-compose up）
mvn test                  # 运行测试
mvn clean package         # 打包
```

## 开发原则

- Pipeline 架构、环境变量配置、测试覆盖、无硬编码密码
- 单体优先，不引入 Spring Cloud
- 增量交付，每个 feature 独立可合并
- 对外文档在 docs/，开发笔记在 .claude/notes/

## AI 行为准则

### 三思后写
陈述假设，不确定就问，有更简单方案就提出来。不理解时停下来，不闷头猜。

### 简单优先
最小代码解决问题，不加未要求的功能。单次使用不做抽象，不处理不可能发生的错误。
自问：一个 senior engineer 会觉得过度设计吗？

### 精准改动
只改必须改的，不改相邻代码、注释、格式。匹配现有风格。你创建的 orphan（无用 import/变量/函数）自己清理，不删已有死代码。
自问：每一行改动都能追溯到用户请求吗？

### 目标驱动
把模糊任务转为可验证目标。bug → 写测试重现 → 修到通过。多步骤任务先列计划再执行。
强成功标准让你能独立推进，弱标准需要反复确认。

## 当前上下文

- P0 直播弹幕：已完成并验证（限流/敏感词/广播/落库全部通过）
- P1 房间管理：已完成（4 个 API + live_room 表）
- 下一步：视频弹幕发送 + 分段拉取
- 关键参考：`.claude/notes/specs/002-danmaku-pipeline.md`
