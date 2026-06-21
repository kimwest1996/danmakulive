#!/bin/bash
# Warmup: send a burst of danmaku to pre-warm JIT, Redis pool, Kafka producer.
# Usage: bash scripts/perf/warmup.sh [room_id] [token]
# Defaults to /tmp/perf_room_id and /tmp/perf_token from create_room.sh

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
ROOM_ID="${1:-$(cat /tmp/perf_room_id 2>/dev/null)}"
TOKEN="${2:-$(cat /tmp/perf_token 2>/dev/null)}"

if [ -z "$ROOM_ID" ] || [ -z "$TOKEN" ]; then
  echo "Usage: bash scripts/perf/warmup.sh <room_id> <token>"
  echo "  or run create_room.sh first"
  exit 1
fi

WARMUP_COUNT="${WARMUP_COUNT:-500}"
CONCURRENT="${CONCURRENT:-10}"

echo ">>> Warming up: $WARMUP_COUNT requests, c$CONCURRENT"

# Generate payload file
echo '{"content":"warmup danmaku"}' > /tmp/perf_payload.json

# Use ab if available, otherwise sequential curl
if command -v ab &> /dev/null; then
  echo ">>> Using ApacheBench..."
  # First send a few sequential to register user in rate limiter
  for i in $(seq 1 5); do
    curl -s -X POST "$BASE_URL/api/v1/rooms/$ROOM_ID/danmaku" \
      -H "Content-Type: application/json" \
      -H "Authorization: $TOKEN" \
      -d '{"content":"warmup sequential '"$i"'"}' > /dev/null
  done

  ab -n "$WARMUP_COUNT" -c "$CONCURRENT" \
    -p /tmp/perf_payload.json -T "application/json" \
    -H "Authorization: $TOKEN" \
    "$BASE_URL/api/v1/rooms/$ROOM_ID/danmaku"

else
  echo ">>> Using sequential curl (install apachebench 'ab' for faster warmup)..."
  for i in $(seq 1 "$WARMUP_COUNT"); do
    curl -s -X POST "$BASE_URL/api/v1/rooms/$ROOM_ID/danmaku" \
      -H "Content-Type: application/json" \
      -H "Authorization: $TOKEN" \
      -d '{"content":"warmup '"$i"'"}' > /dev/null &
    if [ $((i % 50)) -eq 0 ]; then
      wait
      echo "  $i / $WARMUP_COUNT done..."
    fi
  done
  wait
fi

echo ">>> Warmup complete!"
