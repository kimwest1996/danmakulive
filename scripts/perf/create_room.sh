#!/bin/bash
# Create a test room for performance testing.
# Usage: bash scripts/perf/create_room.sh [room_title]
# Returns: ROOM_ID echoed to stdout, also saved to /tmp/perf_room_id

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
ROOM_TITLE="${1:-perf-test-room-$(date +%s)}"
PERF_EMAIL="perf-test@danmakulive.local"
PERF_PASSWORD="123456"

echo ">>> Registering/Loading perf test user..."
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$PERF_EMAIL\",\"password\":\"$PERF_PASSWORD\"}")

# Check if login succeeded by looking for token
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo ">>> User not registered yet, creating..."
  REG_RESP=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$PERF_EMAIL\",\"password\":\"$PERF_PASSWORD\",\"nickname\":\"PerfTester\"}")
  TOKEN=$(echo "$REG_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null || echo "")
fi

if [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to get auth token"
  exit 1
fi

echo ">>> Creating room: $ROOM_TITLE"
ROOM_RESP=$(curl -s -X POST "$BASE_URL/api/v1/rooms" \
  -H "Content-Type: application/json" \
  -H "Authorization: $TOKEN" \
  -d "{\"title\":\"$ROOM_TITLE\"}")

ROOM_ID=$(echo "$ROOM_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

if [ -z "$ROOM_ID" ]; then
  echo "ERROR: Failed to create room. Response: $ROOM_RESP"
  exit 1
fi

echo ">>> Room created: $ROOM_ID"
echo "$ROOM_ID" > /tmp/perf_room_id
echo "$TOKEN" > /tmp/perf_token

echo ""
echo "Room ID:  $ROOM_ID"
echo "Token:    $TOKEN"
echo "Saved to: /tmp/perf_room_id, /tmp/perf_token"
echo ""
echo "Next: bash scripts/perf/warmup.sh && bash scripts/perf/run_all.sh"
