# DanmakuLive

High-concurrency live-streaming real-time danmaku (bullet comment) backend.

## Architecture

```
Client (WebSocket/STOMP)
  → RateLimit (Redis Lua sliding window)
  → SensitiveWord (DFA trie, <1ms)
  → Kafka (async persistence)
  → Redis Pub/Sub (cross-node broadcast)
  → All connected clients receive danmaku
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.2 + Java 17 |
| Database | MySQL 8 + MyBatis-Plus |
| Cache | Redis 7 (Pub/Sub, rate limiting, token store) |
| Message Queue | Kafka 3.x (persistence, backpressure) |
| WebSocket | Spring WebSocket + STOMP |
| Security | BCrypt (spring-security-crypto) |

## Quick Start

```bash
# Start dependencies
docker-compose up -d

# Run application
mvn spring-boot:run

# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"123456","nickname":"Test"}'
```

## Performance

| Metric | Target | Status |
|--------|--------|--------|
| Concurrent connections/node | 5000+ | Pending |
| P99 danmaku broadcast latency | < 50ms | Pending |
| Kafka persistence throughput | > 5000 msg/s | Pending |

## Documentation

- [Architecture](docs/architecture.md)
- [Performance Report](docs/performance.md)

## License

MIT
