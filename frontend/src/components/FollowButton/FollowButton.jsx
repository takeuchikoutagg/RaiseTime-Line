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
          // 「フォロー解除」はマウスを乗せたときしか見えないので、読み上げでは押すと解除されることも伝える
          aria-label={`フォロー中（押すと @${username} のフォローを解除）`}
        >
          <span className={styles.default}>フォロー中</span>
          <span className={styles.hover} aria-hidden="true">
            フォロー解除
          </span>
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
