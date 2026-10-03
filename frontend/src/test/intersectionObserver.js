import { act, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

// jsdom には IntersectionObserver が無いので、テスト用に置き換える。
// scrollToBottom() で「一覧の一番下の目印が画面に入った」ことを起こせる
export function installIntersectionObserverMock() {
  const observers = new Set()

  class MockIntersectionObserver {
    constructor(callback) {
      this.callback = callback
      this.targets = new Set()
      observers.add(this)
    }

    observe(target) {
      this.targets.add(target)
    }

    unobserve(target) {
      this.targets.delete(target)
    }

    disconnect() {
      this.targets.clear()
      observers.delete(this)
    }
  }

  vi.stubGlobal('IntersectionObserver', MockIntersectionObserver)

  function observedCount() {
    return [...observers].reduce((sum, observer) => sum + observer.targets.size, 0)
  }

  function scrollToBottom() {
    for (const observer of [...observers]) {
      const entries = [...observer.targets].map((target) => ({ target, isIntersecting: true }))
      if (entries.length > 0) observer.callback(entries, observer)
    }
  }

  return {
    // 今見張っている目印があれば「画面に入った」ことにする（見張っていなければ何も起きない）
    scrollToBottom,
    // 目印を見張り始めるのを待ってから、一番下までスクロールしたことにする。
    // 読み込み後の effect（見張りの開始）より先にスクロールしてしまい、テストがときどき失敗するのを防ぐ
    async scrollToBottomWhenObserved() {
      await waitFor(() => {
        if (observedCount() === 0) throw new Error('まだ目印を見張っていない')
      })
      act(() => scrollToBottom())
    },
    observedCount,
  }
}
