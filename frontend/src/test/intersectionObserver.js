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

  return {
    scrollToBottom() {
      for (const observer of [...observers]) {
        const entries = [...observer.targets].map((target) => ({ target, isIntersecting: true }))
        if (entries.length > 0) observer.callback(entries, observer)
      }
    },
    observedCount() {
      return [...observers].reduce((sum, observer) => sum + observer.targets.size, 0)
    },
  }
}
