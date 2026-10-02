import { describe, expect, it } from 'vitest'
import { formatDateTime, formatRelativeTime } from './time.js'

const NOW = new Date('2026-10-02T12:00:00').getTime()
const ago = (seconds) => new Date(NOW - seconds * 1000).toISOString()

describe('formatRelativeTime', () => {
  it('1分未満は「たった今」', () => {
    expect(formatRelativeTime(ago(0), NOW)).toBe('たった今')
    expect(formatRelativeTime(ago(59), NOW)).toBe('たった今')
  })

  it('分・時間・日', () => {
    expect(formatRelativeTime(ago(3 * 60), NOW)).toBe('3分前')
    expect(formatRelativeTime(ago(5 * 3600), NOW)).toBe('5時間前')
    expect(formatRelativeTime(ago(2 * 86400), NOW)).toBe('2日前')
  })

  it('7日以上前は日付', () => {
    expect(formatRelativeTime(ago(8 * 86400), NOW)).toBe('2026/09/24')
  })
})

describe('formatDateTime', () => {
  it('YYYY/MM/DD HH:mm（ブラウザのタイムゾーン）', () => {
    expect(formatDateTime(new Date('2026-10-02T09:05:00').toISOString())).toBe('2026/10/02 09:05')
  })
})
