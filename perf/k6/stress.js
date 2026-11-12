import { ensureSeeded } from './lib/api.js'
import { commonOptions, visit } from './lib/scenario.js'

// ストレステスト：人数を段階的に増やし、どこから遅くなるか・エラーが出始めるかを見る（docs/パフォーマンステスト.md 4章）。
// 限界を探すのが目的なので、合格ラインでは判定しない。ウェブダッシュボードの HTML レポートで、
// 人数のグラフと応答時間・エラー率のグラフを見比べて、悪くなり始めた人数を読み取る
export const options = {
  ...commonOptions,
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '2m', target: 200 },
        { duration: '3m', target: 200 },
        { duration: '2m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
}

export function setup() {
  ensureSeeded()
}

export default function () {
  visit()
}
