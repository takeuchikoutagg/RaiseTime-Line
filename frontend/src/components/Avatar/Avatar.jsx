import { useState } from 'react'
import styles from './Avatar.module.css'

// アイコン（F-12）。プロフィール画像があれば丸く切り抜いて表示し、無ければ（または読み込めなければ）
// 名前の1文字目を、@ユーザーID から決めた色の丸に表示する（prototype と同じ）。
// 名前はすぐそばに文字で出ているので、読み上げ用には何も伝えない（aria-hidden）
export default function Avatar({ username, name, avatarUrl = null, size = 'medium' }) {
  // 読み込めなかった画像の URL。別の画像に変わったら、もう一度表示を試す
  const [failedUrl, setFailedUrl] = useState(null)
  const showImage = avatarUrl != null && avatarUrl !== failedUrl

  let hue = 0
  for (const ch of username) {
    hue = (hue * 31 + ch.codePointAt(0)) % 360
  }
  const initial = [...name][0] ?? '?'
  return (
    <span
      className={`${styles.avatar} ${styles[size]}`}
      style={showImage ? undefined : { background: `hsl(${hue}, 55%, 50%)` }}
      aria-hidden="true"
    >
      {showImage ? (
        <img className={styles.image} src={avatarUrl} alt="" onError={() => setFailedUrl(avatarUrl)} />
      ) : (
        initial
      )}
    </span>
  )
}
