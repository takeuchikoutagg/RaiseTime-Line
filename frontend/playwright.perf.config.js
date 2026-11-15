import { defineConfig } from '@playwright/test'

// ブラウザのパフォーマンステスト（docs/パフォーマンステスト.md 10章）。毎回の npm run e2e には入れず、必要なときに実行する。
// 実行：npm run e2e:perf（または perf/run.sh browser）
// - 本番用のビルドを preview.sh（ポート 4174）で配って測る。開発サーバー（5174）は本番と配り方が違い、遅く出るため
// - データは性能テスト用の DB（raisetime_perf）。先に perf/run.sh backend と perf/run.sh seed を実行しておく
// - 通信と CPU の速さを制限するには Chromium の機能（CDP）が要るので、Chromium だけで動かす
// - 測る条件（PC・スマホ相当）は e2e-perf/measure.js の PROFILES。プロジェクトの名前で選ぶ
export default defineConfig({
  testDir: './e2e-perf',
  // 測っている間に、ほかのテストが CPU を取り合わないよう、1つずつ順に動かす
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 300_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4174',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },
  globalSetup: './e2e-perf/global-setup.js',
  projects: [
    { name: 'pc', use: { browserName: 'chromium' } },
    { name: 'mobile', use: { browserName: 'chromium' } },
  ],
  webServer: {
    command: './preview.sh',
    url: 'http://localhost:4174',
    // 測る前に必ずビルドし直すため、動いているものは使わない（preview.sh がポートのプロセスを止めてから起動する）
    reuseExistingServer: false,
    timeout: 120_000,
  },
})
