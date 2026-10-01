import styles from './Avatar.module.css'

// アイコン画像の機能はまだ無いので、名前の1文字目を、@ユーザーID から決めた色の丸に表示する（prototype と同じ）
export default function Avatar({ username, name, size = 'medium' }) {
  let hue = 0
  for (const ch of username) {
    hue = (hue * 31 + ch.codePointAt(0)) % 360
  }
  const initial = [...name][0] ?? '?'
  return (
    <span className={`${styles.avatar} ${styles[size]}`} style={{ background: `hsl(${hue}, 55%, 50%)` }} aria-hidden="true">
      {initial}
    </span>
  )
}
