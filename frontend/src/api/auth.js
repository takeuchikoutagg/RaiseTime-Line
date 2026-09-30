import { request } from './client.js'

// 認証の API（docs/機能定義書/F-01_新規登録.md・F-02_ログイン・ログアウト.md）

// CSRF トークンの Cookie（XSRF-TOKEN）を受け取る。画面を開いたときに最初に呼ぶ
export function fetchCsrf() {
  return request('/api/auth/csrf')
}

// { username, name } を返す。アクセストークンが切れていれば、client.js が自動でトークンを更新する
export function fetchMe() {
  return request('/api/auth/me')
}

export function login({ email, password }) {
  return request('/api/auth/login', { method: 'POST', body: { email, password } })
}

export function signup({ name, username, email, password }) {
  return request('/api/auth/signup', { method: 'POST', body: { name, username, email, password } })
}

export function logout() {
  return request('/api/auth/logout', { method: 'POST' })
}
