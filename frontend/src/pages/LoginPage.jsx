import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth.js'
import styles from './AuthPage.module.css'

// S-01 ログイン（docs/機能定義書/F-02_ログイン・ログアウト.md）
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (!email.trim() || !password) {
      setError('メールアドレスとパスワードを入力してください')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await login({ email, password })
      navigate('/', { replace: true })
    } catch (e) {
      // 401 のときは「メールアドレスまたはパスワードが正しくありません」（どちらが違うかは伝えない）
      setError(e.message)
      setSubmitting(false)
    }
  }

  return (
    <main className={styles.auth}>
      <p className={styles.logo}>RaiseTime Line</p>
      <h1 className={styles.title}>ログイン</h1>
      <form onSubmit={handleSubmit} noValidate>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="login-email">
            メールアドレス
          </label>
          <input
            id="login-email"
            className={styles.input}
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="login-password">
            パスワード
          </label>
          <input
            id="login-password"
            className={styles.input}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <p className={styles.formError} role="alert">
          {error}
        </p>
        <button type="submit" className={styles.submit} disabled={submitting}>
          {submitting ? 'ログイン中…' : 'ログイン'}
        </button>
      </form>
      <p className={styles.switch}>
        アカウントをお持ちでない方は <Link to="/signup">新規登録</Link>
      </p>
    </main>
  )
}
