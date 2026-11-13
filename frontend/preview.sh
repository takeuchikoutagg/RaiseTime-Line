#!/bin/bash
# 本番用のビルドを作り、ポート 4174 で配る（ブラウザのパフォーマンステスト用。docs/パフォーマンステスト.md 10章）。
# start.sh と同じく、ポートが使用中なら、そのプロセスを止めてから同じポートで起動する（別のポートに逃がさない）
set -e

PORT=4174
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PID=$(lsof -ti tcp:"${PORT}" -sTCP:LISTEN || true)
if [ -n "${PID}" ]; then
  echo "ポート ${PORT} は使用中です (PID: ${PID})。プロセスを停止します..."
  kill ${PID}
  sleep 1
  PID=$(lsof -ti tcp:"${PORT}" -sTCP:LISTEN || true)
  if [ -n "${PID}" ]; then
    echo "プロセスが残っているため強制終了します (PID: ${PID})..."
    kill -9 ${PID}
  fi
fi

cd "${SCRIPT_DIR}"
npm run build
npm run preview
