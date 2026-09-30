import { Navigate, Outlet } from 'react-router'
import Loading from '../components/Loading/Loading.jsx'
import { useAuth } from './useAuth.js'

// ログインが必要な画面。ログインしていなければログイン画面へ移動する
export function RequireAuth() {
  const { status, user } = useAuth()
  if (status === 'loading') return <Loading />
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

// ログイン画面・新規登録画面。ログイン中ならホームへ移動する
export function PublicOnly() {
  const { status, user } = useAuth()
  if (status === 'loading') return <Loading />
  return user ? <Navigate to="/" replace /> : <Outlet />
}
