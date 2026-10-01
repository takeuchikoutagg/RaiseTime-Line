// 投稿日時の表示（docs/画面設計.md S-03：「3分前」などの相対表示。マウスを乗せると日時）

const MINUTE = 60
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function pad2(n) {
  return String(n).padStart(2, '0')
}

// 2026/10/02 09:05
export function formatDateTime(iso) {
  const d = new Date(iso)
  return `${d.getFullYear()}/${pad2(d.getMonth() + 1)}/${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

// たった今 / 3分前 / 5時間前 / 2日前 / 7日以上前は日付（2026/10/02）
export function formatRelativeTime(iso, now = Date.now()) {
  const seconds = Math.floor((now - new Date(iso).getTime()) / 1000)
  if (seconds < MINUTE) return 'たった今'
  if (seconds < HOUR) return `${Math.floor(seconds / MINUTE)}分前`
  if (seconds < DAY) return `${Math.floor(seconds / HOUR)}時間前`
  if (seconds < 7 * DAY) return `${Math.floor(seconds / DAY)}日前`
  return formatDateTime(iso).slice(0, 10)
}
