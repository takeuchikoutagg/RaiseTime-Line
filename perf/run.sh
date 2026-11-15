#!/bin/bash
# 性能テストの入口（docs/パフォーマンステスト.md 6章）。毎回自動では動かさず、必要なときに手で実行する。
#
#   perf/run.sh backend   性能テスト用の DB（raisetime_perf）でバックエンドを起動する（別のターミナルで動かしたままにする）
#   perf/run.sh seed      raisetime_perf に大量のデータを入れる（入れ直すときも同じ）
#   perf/run.sh smoke     1人で1分（スクリプトの確認と基準の値）
#   perf/run.sh load      同時50人で約8分（合格ラインの判定）。人数は VUS=30 perf/run.sh load のように変えられる
#   perf/run.sh stress    50 → 100 → 200人と増やして約17分（どこから遅くなるかを見る）
#   perf/run.sh browser   ブラウザで画面の表示・操作の速さを測る（本番用のビルド・Chromium・PC とスマホ相当。約10分）
#   perf/run.sh explain   主要な SQL の実行計画と実行時間
#   perf/run.sh reset     raisetime_perf を消す
#
# 開発用の DB（raisetime）には触れない。隣の Taskmanagement のポート（8080 / 5173 / 5433）にも触れない。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

PERF_DB=raisetime_perf
DB_USER=raisetime

usage() {
  sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
}

psql_in() {
  docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USER}" "$@"
}

require_postgres() {
  if ! docker compose exec -T postgres pg_isready -U "${DB_USER}" > /dev/null 2>&1; then
    echo "PostgreSQL が動いていません。先に docker compose up -d を実行してください" >&2
    exit 1
  fi
}

perf_db_exists() {
  [ "$(psql_in -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '${PERF_DB}'")" = "1" ]
}

create_perf_db() {
  if ! perf_db_exists; then
    echo "性能テスト用の DB（${PERF_DB}）を作ります"
    psql_in -d postgres -c "CREATE DATABASE ${PERF_DB}"
  fi
}

run_k6() {
  local name="$1"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  mkdir -p perf/results
  echo "k6 を実行します：${name}（結果：perf/results/${name}-${stamp}.json と .html）"
  echo "実行中のグラフは http://localhost:5665 で見られます"
  docker compose --profile perf run --rm --service-ports \
    -e K6_WEB_DASHBOARD=true \
    -e K6_WEB_DASHBOARD_EXPORT="/results/${name}-${stamp}.html" \
    -e VUS="${VUS:-}" \
    k6 run --summary-export "/results/${name}-${stamp}.json" "/scripts/${name}.js"
}

case "${1:-}" in
  backend)
    require_postgres
    create_perf_db
    # start.sh はそのまま使う（ポート 8081 が使用中なら、そのプロセスを止めてから起動する）。
    # 接続先の DB だけを環境変数で性能テスト用に変える。テーブルは起動時に Flyway が作る
    echo "バックエンドを ${PERF_DB} で起動します（終わったら Ctrl+C で止め、普段どおり backend/start.sh で起動し直してください）"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5434/${PERF_DB}"
    exec backend/start.sh
    ;;
  seed)
    require_postgres
    create_perf_db
    if [ "$(psql_in -d "${PERF_DB}" -tAc "SELECT to_regclass('public.posts') IS NOT NULL")" != "t" ]; then
      echo "テーブルがまだありません。先に別のターミナルで perf/run.sh backend を起動してください（Flyway がテーブルを作ります）" >&2
      exit 1
    fi
    echo "大量のデータを入れます（数分かかります）"
    SECONDS=0
    psql_in -d "${PERF_DB}" < perf/seed/seed.sql
    echo "完了しました（${SECONDS}秒）"
    ;;
  smoke | load | stress)
    run_k6 "$1"
    ;;
  browser)
    # 本番用のビルドを frontend/preview.sh（ポート 4174）で配り、Playwright で測る（frontend/playwright.perf.config.js）。
    # 測るたびにビルドし直すため、前に動かしたプレビューが 4174 に残っていれば止める（Playwright は使用中のポートでは始めない）
    PREVIEW_PID=$(lsof -ti tcp:4174 -sTCP:LISTEN || true)
    if [ -n "${PREVIEW_PID}" ]; then
      echo "ポート 4174 のプレビュー（PID: ${PREVIEW_PID}）を止めます"
      kill ${PREVIEW_PID}
      sleep 1
    fi
    cd frontend
    npm run e2e:perf
    ;;
  explain)
    require_postgres
    if ! perf_db_exists; then
      echo "${PERF_DB} がありません。perf/run.sh backend と perf/run.sh seed を先に実行してください" >&2
      exit 1
    fi
    psql_in -d "${PERF_DB}" < perf/sql/explain.sql
    ;;
  reset)
    require_postgres
    # 性能テスト用の DB だけを消す。つながっているバックエンドがあれば、その接続も切る
    psql_in -d postgres -c "DROP DATABASE IF EXISTS ${PERF_DB} WITH (FORCE)"
    echo "${PERF_DB} を消しました。バックエンドを perf/run.sh backend で動かしていたら止め、backend/start.sh で起動し直してください"
    ;;
  *)
    usage
    ;;
esac
