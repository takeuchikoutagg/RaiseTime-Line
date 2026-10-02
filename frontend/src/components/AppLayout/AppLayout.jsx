import { useCallback, useState } from 'react'
import { Link, Outlet } from 'react-router'
import { useAuth } from '../../auth/useAuth.js'
import ComposeModal from '../ComposeModal/ComposeModal.jsx'
import styles from './AppLayout.module.css'

// ログイン後の画面の共通部分（docs/画面設計.md 3章の共通ヘッダー）。
// 検索・マイページは、その機能ができてから足す。
// 投稿モーダルはヘッダーの「投稿」ボタンからも開くので、ここで持つ。作成した投稿は Outlet の context で画面に渡す
export default function AppLayout() {
  const { logout } = useAuth()
  const [composeOpen, setComposeOpen] = useState(false)
  const [createdPost, setCreatedPost] = useState(null)
  const [loggingOut, setLoggingOut] = useState(false)

  const openCompose = useCallback(() => setComposeOpen(true), [])
  const closeCompose = useCallback(() => setComposeOpen(false), [])

  function handleCreated(post) {
    setCreatedPost(post)
    setComposeOpen(false)
  }

  async function handleLogout() {
    setLoggingOut(true)
    // ログアウトすると、ルートの設定（RequireAuth）によりログイン画面へ移動する
    await logout()
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.logo}>
            RaiseTime Line
          </Link>
          <nav className={styles.nav} aria-label="メイン">
            <Link to="/" className={styles.navLink}>
              ホーム
            </Link>
            <button type="button" className={`${styles.navLink} ${styles.postButton}`} onClick={openCompose}>
              投稿
            </button>
            <button type="button" className={styles.navLink} onClick={handleLogout} disabled={loggingOut}>
              ログアウト
            </button>
          </nav>
        </div>
      </header>
      <main className={styles.main}>
        <Outlet context={{ openCompose, createdPost }} />
      </main>
      {composeOpen && <ComposeModal onClose={closeCompose} onCreated={handleCreated} />}
    </div>
  )
}
