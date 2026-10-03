import { useEffect, useRef } from 'react'

// 無限スクロール。返した ref を一覧の一番下に置いた目印（空の div など）に付けると、
// enabled の間、その目印が画面に近づいたとき（rootMargin の分だけ手前）に onLoadMore を呼ぶ。
// 読み込み中・続きが無い・失敗したときは enabled を false にして、何度も呼ばれないようにする
export function useInfiniteScroll({ enabled, onLoadMore, rootMargin = '200px' }) {
  const sentinelRef = useRef(null)

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!enabled || !sentinel) return undefined
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) onLoadMore()
      },
      { rootMargin },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [enabled, onLoadMore, rootMargin])

  return sentinelRef
}
