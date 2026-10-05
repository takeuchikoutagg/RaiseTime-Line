import { defineConfig, devices } from '@playwright/test'

// E2E テスト（docs/テスト仕様書.md のシナリオを、本物のブラウザで自動で動かす）。
// 実行：npm run e2e（PostgreSQL・S3Mock は先に `docker compose up -d` で起動しておく）
// - Chromium（Chrome）・WebKit（Safari と同じ描画の仕組み）・Microsoft Edge（インストール済みのもの）の3つで動かす
// - サーバーが動いていなければ start.sh で起動する。動いていればそのまま使う（ポートは 8081 / 5174 のまま）
export default defineConfig({
  testDir: './e2e',
  // テストごとに別のユーザーを作るので、並べて動かしても互いに影響しない。
  // 開発用のサーバー1台を相手にするので、同時に動かす数は抑える
  fullyParallel: true,
  workers: 3,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5174',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    // 失敗したときだけ、原因を調べるための記録（操作の流れ・スクリーンショット）を残す
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    { name: 'msedge', use: { ...devices['Desktop Edge'], channel: 'msedge' } },
  ],
  webServer: [
    {
      command: './start.sh',
      cwd: '../backend',
      url: 'http://localhost:8081/actuator/health',
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      command: './start.sh',
      url: 'http://localhost:5174',
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
})
