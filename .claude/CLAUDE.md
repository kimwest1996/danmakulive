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

## 包结构（按特征分包）

```
com.danmakulive/
├── auth/         用户认证（注册、登录、TokenInterceptor）
├── room/         直播间管理
├── danmaku/      弹幕 Pipeline
├── broadcast/    跨节点广播（Redis Pub/Sub）
├── common/       通用基础设施（Result、异常、BaseDO）
└── dev/          开发调试端点（@Profile("dev")）
```

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

- 遵循 v1 决策前提：Pipeline 架构、环境变量配置、测试覆盖、无硬编码密码
- 单体优先，不引入 Spring Cloud
- 增量交付，每个 feature 独立可合并
- 对外文档在 docs/，开发笔记在 .claude/notes/
