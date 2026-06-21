#!/bin/bash
# Run all performance tests in sequence.
# Prerequisites: docker-compose up, app running on :8080, room created (create_room.sh)
# Usage: bash scripts/perf/run_all.sh [room_id] [token]

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
ROOM_ID="${1:-$(cat /tmp/perf_room_id 2>/dev/null)}"
TOKEN="${2:-$(cat /tmp/perf_token 2>/dev/null)}"

if [ -z "$ROOM_ID" ] || [ -z "$TOKEN" ]; then
  echo "Usage: bash scripts/perf/run_all.sh <room_id> <token>"
  echo "  or run create_room.sh first"
  exit 1
fi

echo "=========================================="
echo " DanmakuLive Performance Test Suite"
echo "=========================================="
echo "Room:  $ROOM_ID"
echo "API:   $BASE_URL"
echo ""

OUT_DIR="docs/perf"
mkdir -p "$OUT_DIR"
REPORT="$OUT_DIR/$(date +%Y-%m-%d)-report.md"

# Header
cat > "$REPORT" << REPORT_HEADER
# DanmakuLive 性能测试报告

## 测试环境

| 项目 | 规格 |
|------|------|
| 日期 | $(date +%Y-%m-%d) |
| Room ID | $ROOM_ID |
| Java | $(java -version 2>&1 | head -1) |
| API | $BASE_URL |

---

REPORT_HEADER

# ---------- 1. Pipeline HTTP Throughput ----------
echo ">>> [1/4] Pipeline HTTP Throughput (ab)..."
echo "" >> "$REPORT"
echo "## 场景 1：Pipeline 全链路吞吐 (HTTP)" >> "$REPORT"
echo "" >> "$REPORT"

echo '{"content":"ab throughput test"}' > /tmp/perf_payload.json

for c in 1 10 50 100; do
  echo "  c$c ..."
  RESULT=$(ab -n 200 -c "$c" \
    -p /tmp/perf_payload.json -T "application/json" \
    -H "Authorization: $TOKEN" \
    "$BASE_URL/api/v1/rooms/$ROOM_ID/danmaku" 2>&1)

  QPS=$(echo "$RESULT" | grep "Requests per second" | awk '{print $4}')
  P50=$(echo "$RESULT" | grep "50%" | awk '{print $2}')
  P90=$(echo "$RESULT" | grep "90%" | awk '{print $2}')
  P99=$(echo "$RESULT" | grep "99%" | awk '{print $2}')
  FAILED=$(echo "$RESULT" | grep "Failed requests" | awk '{print $3}')

  echo "| c$c | $QPS | $P50 | $P90 | $P99 | $FAILED |" >> "$REPORT"
done

echo "" >> "$REPORT"

# ---------- 2. Build JMH benchmarks ----------
echo ">>> [2/4] Building JMH benchmarks..."
mvn clean test-compile -DskipTests -q 2>&1 | tail -1

# ---------- 3. Run JUnit perf tests ----------
echo ">>> [3/4] Running JUnit perf tests..."
mvn test -Dtest="com.danmakulive.perf.RateLimiterPerfTest" -pl . -Dspring.profiles.active=dev 2>&1 | grep -A20 "=== RateLimiter" | tee /tmp/perf_ratelimit.txt

cat >> "$REPORT" << 'REPORT_MID'
## 场景 2：Rate Limiter 性能

| 指标 | 值 |
|------|-----|
| 单次 tryAcquire | (参见 JUnit 输出) |
| 限流正确性 | (参见 JUnit 测试结果) |

---

## 场景 3：WebSocket 广播延迟

(需要手动运行: java com.danmakulive.perf.WebSocketBroadcastLatencyTest <subscribers> <messages> <roomId> <token>)

---

## 场景 4：WebSocket 连接容量

(需要手动运行: java com.danmakulive.perf.WebSocketCapacityTest <roomId>)

---

## 场景 5：Kafka Consumer 批量持久化

(需要手动验证: 发送弹幕 → 检查 MySQL live_danmaku 表)

---

## 踩坑记录

| # | 问题 | 原因 | 解决 |
|---|------|------|------|

---

## 最终数据总览

| 维度 | 指标 | 数值 |
|------|------|------|
| **吞吐** | 峰值 QPS | TBD |
| **广播延迟** | 50/100 订阅者 | TBD |
| **连接容量** | 最大并发 | TBD |
| **持久化** | Kafka → MySQL | TBD |
| **限流** | Lua 执行延迟 | TBD |

---

## 简历话术

```
(压测完成后填写)
```
REPORT_MID

echo ""
echo ">>> Report generated: $REPORT"
echo ">>> Done! Manual steps remaining:"
echo "  1. Run JMH: java -jar target/benchmarks.jar (requires Redis)"
echo "  2. Broadcast latency: java com.danmakulive.perf.WebSocketBroadcastLatencyTest 50 100 $ROOM_ID $TOKEN"
echo "  3. Connection capacity: java com.danmakulive.perf.WebSocketCapacityTest $ROOM_ID"
echo "  4. Check Kafka→MySQL: docker exec -it danmakulive-kafka ...  + SELECT * FROM live_danmaku;"
echo "  5. Fill in results in $REPORT"
