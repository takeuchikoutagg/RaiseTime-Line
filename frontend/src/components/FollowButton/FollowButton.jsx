import { useEffect, useState } from 'react'
import { followUser, unfollowUser } from '../../api/users.js'
import styles from './FollowButton.module.css'

// フォローの失敗メッセージを出しておく時間
export const FOLLOW_ERROR_DURATION_MS = 3000

// フォローボタン（docs/機能定義書/F-07_フォロー.md。prototype の followButtonHtml と同じ見た目）。
// 未フォロー「フォローする」、フォロー中「フォロー中」（マウスを乗せると「フォロー解除」）。
// 押したら API の結果でボタンとフォロワー数を更新する（onChange）。解除も確認なしですぐ行う。通信中は押せない
export default function FollowButton({ username, followedByMe, onChange }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!error) return undefined
    const timer = setTimeout(() => setError(''), FOLLOW_ERROR_DURATION_MS)
    return () => clearTimeout(timer)
  }, [error])

  async function handleClick() {
    setPending(true)
    setError('')
    try {
      const result = followedByMe ? await unfollowUser(username) : await followUser(username)
      onChange(result)
    } catch (e) {
      setError(e.message)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className={styles.wrapper}>
      {followedByMe ? (
        <button
          type="button"
          className={`${styles.button} ${styles.following}`}
          onClick={handleClick}
          disabled={pending}
        >
          {/*
            読み上げる名前は、見えない文字の「フォロー中（押すと @… のフォローを解除）」だけにする。
            見えている「フォロー中」「フォロー解除」は、マウスを乗せると入れ替わる（display: none になる）ため、
            読み上げの対象にすると、マウスの位置でボタンの名前が変わってしまう（E2E テストで見つかった）
          */}
          <span className={styles.default} aria-hidden="true">
            フォロー中
          </span>
          <span className={styles.hover} aria-hidden="true">
            フォロー解除
          </span>
          <span className={styles.visuallyHidden}>フォロー中（押すと @{username} のフォローを解除）</span>
        </button>
      ) : (
        <button type="button" className={`${styles.button} ${styles.follow}`} onClick={handleClick} disabled={pending}>
          フォローする
        </button>
      )}
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
