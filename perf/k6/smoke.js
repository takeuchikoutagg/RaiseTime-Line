import { ensureSeeded } from './lib/api.js'
import { commonOptions, thresholds, visit } from './lib/scenario.js'

// スモークテスト：1人で1分。スクリプトが正しく動くかと、負荷がないときの基準の値を確かめる。
// 負荷テスト（load.js）の前に、まずこれが通ることを確かめる
export const options = {
  ...commonOptions,
  vus: 1,
  duration: '1m',
  thresholds,
}

export function setup() {
  ensureSeeded()
}

export default function () {
  visit()
}
