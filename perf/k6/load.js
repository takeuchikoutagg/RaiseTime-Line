import { ensureSeeded } from './lib/api.js'
import { commonOptions, thresholds, visit } from './lib/scenario.js'

// 負荷テスト：想定する利用状況（同時に50人）で、合格ラインを満たすかを確かめる（docs/パフォーマンステスト.md 4章）。
// 人数は環境変数 VUS で変えられる（例：VUS=30 perf/run.sh load）
const VUS = Number(__ENV.VUS || 50)

export const options = {
  ...commonOptions,
  scenarios: {
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: VUS }, // ゆっくり増やす（ログインが一度に集中しないように）
        { duration: '5m', target: VUS }, // 想定の人数を保つ
        { duration: '1m', target: 0 }, // 減らす
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds,
}

export function setup() {
  ensureSeeded()
}

export default function () {
  visit()
}
