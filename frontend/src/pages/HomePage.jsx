import { useState } from 'react'
import { useAuth } from '../auth/useAuth.js'
import styles from './HomePage.module.css'

// 仮のホーム画面。F2 でタイムライン（S-03）に置き換える
export default function HomePage() {
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setLoggingOut(true)
    // ログアウトすると、ルートの設定（RequireAuth）によりログイン画面へ移動する
    await logout()
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <span className={styles.logo}>RaiseTime Line</span>
        <button type="button" className={styles.logout} onClick={handleLogout} disabled={loggingOut}>
          ログアウト
        </button>
      </header>
      <main className={styles.main}>
        <h1 className={styles.hello}>Hello World</h1>
        <p className={styles.welcome}>
          ようこそ、{user.name}（@{user.username}）さん
        </p>
        <p className={styles.note}>タイムラインは準備中です。</p>
      </main>
    </div>
  )
}
