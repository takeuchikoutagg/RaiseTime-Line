import styles from './Loading.module.css'

// <output> は読み上げソフトに「状態のお知らせ」として伝わる（role="status" と同じ）
export default function Loading() {
  return <output className={styles.loading}>読み込み中…</output>
}
