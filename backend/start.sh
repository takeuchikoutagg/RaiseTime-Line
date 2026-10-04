#!/bin/bash
set -e

PORT=8081
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PID=$(lsof -ti tcp:"$PORT" -sTCP:LISTEN || true)
if [ -n "$PID" ]; then
  echo "ポート $PORT は使用中です (PID: $PID)。プロセスを停止します..."
  kill $PID
  sleep 1
  PID=$(lsof -ti tcp:"$PORT" -sTCP:LISTEN || true)
  if [ -n "$PID" ]; then
    echo "プロセスが残っているため強制終了します (PID: $PID)..."
    kill -9 $PID
  fi
fi

cd "$SCRIPT_DIR"
# 開発用の設定（application-dev.properties）を使う
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
./gradlew bootRun
