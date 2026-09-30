import { useCallback, useEffect, useMemo, useState } from 'react'
import * as authApi from '../api/auth.js'
import { setUnauthorizedHandler } from '../api/client.js'
import { AuthContext } from './authContext.js'

// 画面を開いたときに、ログイン中かどうかを確認する。
// 1. CSRF トークンの Cookie を受け取る  2. me を呼ぶ（アクセストークンが切れていれば client.js が自動で更新する）
export default function AuthProvider({ children }) {
  const [status, setStatus] = useState('loading')
  const [user, setUser] = useState(null)

  useEffect(() => {
    // どこかの API でトークンの更新にも失敗したら、ログアウトした状態にする（→ ログイン画面へ移動する）
    setUnauthorizedHandler(() => setUser(null))

    let cancelled = false
    async function restore() {
      try {
        await authApi.fetchCsrf()
        const me = await authApi.fetchMe()
        if (!cancelled) setUser(me)
      } catch {
        if (!cancelled) setUser(null)
      } finally {
        if (!cancelled) setStatus('ready')
      }
    }
    restore()
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (credentials) => {
    setUser(await authApi.login(credentials))
  }, [])

  const signup = useCallback(async (values) => {
    setUser(await authApi.signup(values))
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      // 通信に失敗しても、画面はログアウトした状態にする
      setUser(null)
    }
  }, [])

  const value = useMemo(() => ({ status, user, login, signup, logout }), [status, user, login, signup, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
