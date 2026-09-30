import { createContext } from 'react'

// ログイン状態を画面全体に配るための Context。中身は AuthProvider.jsx が用意する
// { status: 'loading' | 'ready', user: { username, name } | null, login, signup, logout }
export const AuthContext = createContext(null)
