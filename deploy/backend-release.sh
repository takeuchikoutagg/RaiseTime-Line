#!/bin/bash
# EC2 の上で、バックエンドを新しい版にする（docs/デプロイ.md 3章）。GitHub Actions（deploy.yml）が SSM で root として実行する。
#   使い方：backend-release.sh <新しい jar の S3 の URI> <コミット>
# 1. 新しい jar を取ってきて、今の jar を app.jar.previous として残してから差し替える
# 2. アプリ（systemd の raisetime）を再起動し、/actuator/health が UP になるまで待つ
# 3. UP にならなければ、前の jar に戻して再起動し、失敗として終わる（GitHub Actions のデプロイも失敗になる）
set -euo pipefail

JAR_URI="$1"
RELEASE_SHA="$2"

# 置き場所などは、動作の確認（docs/デプロイ.md 7章）のときだけ環境変数で変える
APP_DIR="${APP_DIR:-/opt/raisetime}"
SERVICE=raisetime
APP_USER="${APP_USER:-raisetime}"
# ALB のヘルスチェックと同じところを見る（本番は SERVER_PORT=8080 で動かす。docs/デプロイ.md 4章）
HEALTH_URL="http://localhost:${HEALTH_PORT:-8080}/actuator/health"
# 起動を待つ時間（秒）。起動には Flyway のマイグレーションも含まれる
WAIT_SECONDS="${WAIT_SECONDS:-180}"

wait_healthy() {
  for _ in $(seq 1 "${WAIT_SECONDS}"); do
    if curl -sf "${HEALTH_URL}" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 1
  done
  return 1
}

echo "新しい版を取ってきます：${JAR_URI}（${RELEASE_SHA}）"
aws s3 cp "${JAR_URI}" "${APP_DIR}/app.jar.new"
chown "${APP_USER}:${APP_USER}" "${APP_DIR}/app.jar.new"

if [ -f "${APP_DIR}/app.jar" ]; then
  cp -p "${APP_DIR}/app.jar" "${APP_DIR}/app.jar.previous"
fi
mv "${APP_DIR}/app.jar.new" "${APP_DIR}/app.jar"
# ログに出す版（logging.structured.json.add.version）。systemd の EnvironmentFile で読む
echo "APP_VERSION=${RELEASE_SHA}" > "${APP_DIR}/version.env"

echo "再起動します"
systemctl restart "${SERVICE}"
if wait_healthy; then
  echo "起動しました（${HEALTH_URL} が UP）"
  exit 0
fi

echo "起動を確かめられませんでした。直近のログ："
journalctl -u "${SERVICE}" -n 50 --no-pager || true

if [ -f "${APP_DIR}/app.jar.previous" ]; then
  echo "前の版に戻します"
  mv "${APP_DIR}/app.jar.previous" "${APP_DIR}/app.jar"
  echo "APP_VERSION=rollback-from-${RELEASE_SHA}" > "${APP_DIR}/version.env"
  systemctl restart "${SERVICE}"
  if wait_healthy; then
    echo "前の版で動いています"
  else
    echo "前の版でも起動できません。EC2 のログを確かめてください"
  fi
fi
exit 1
