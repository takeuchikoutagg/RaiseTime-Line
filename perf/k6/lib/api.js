import http from 'k6/http'
import { check, fail } from 'k6'

// 性能テストで API を呼ぶための共通部品（docs/パフォーマンステスト.md 4章）。
// 考え方は画面の frontend/src/api/client.js と、E2E の frontend/e2e/support.js に合わせている
// - ログイン状態（access_token・refresh_token）は Cookie。k6 は VU（仮想ユーザー）ごとに Cookie を持つので、画面と同じように自動で送られる
// - GET 以外では、XSRF-TOKEN Cookie の値を X-XSRF-TOKEN ヘッダーで送る（CSRF 対策）
// - API が 401 を返したら、POST /api/auth/refresh でトークンを更新してから1回だけ呼び直す（アクセストークンは15分で切れる）

export const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8081').replace(/\/$/, '')
export const PASSWORD = 'password123'

// 種類（kind）ごとに合格ラインを分ける：read（読み取り）・write（書き込み）・auth（ログインなど。BCrypt で重いので合格ラインの対象外）
const READ = 'read'
const WRITE = 'write'
const AUTH = 'auth'

function csrfToken() {
  const cookies = http.cookieJar().cookiesForURL(`${BASE_URL}/api/auth/`)
  return (cookies['XSRF-TOKEN'] || [])[0] || ''
}

function send(method, path, body, params) {
  const headers = { ...(params.headers || {}) }
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken()
  return http.request(method, `${BASE_URL}${path}`, body, { ...params, headers })
}

// name：集計用の名前（/api/posts/{postId} のようにパスの形にする。id ごとに別々に集計されないように）
function request(method, path, { body = null, headers = {}, name, kind }) {
  const params = { headers, tags: { name: `${method} ${name}`, kind } }
  let response = send(method, path, body, params)
  if (response.status === 401) {
    const refreshed = send('POST', '/api/auth/refresh', null, { tags: { name: 'POST /api/auth/refresh', kind: AUTH } })
    if (refreshed.status === 200) response = send(method, path, body, params)
  }
  check(response, { [`${method} ${name} が成功`]: (r) => r.status >= 200 && r.status < 300 })
  return response
}

function json(response) {
  try {
    return response.json()
  } catch {
    return null
  }
}

// ---------- ログイン ----------

// seed.sql で作ったユーザーの @ユーザーID。VU の番号から決める（同じ VU はずっと同じ人）。
// 10人に1人は2000人をフォローする人、別の10人に1人は「始めたばかりの人」か「3人だけフォローする人」にする。
// タイムラインは、該当する投稿が少ない人ほど多くの投稿を読むので、後者が最悪ケースになる
export function usernameForVu(vu) {
  if (vu % 10 === 0) return `perf_heavy${((vu / 10) % 5) + 1}`
  if (vu % 10 === 5) return Math.floor(vu / 10) % 2 === 0 ? 'perf_newbie' : 'perf_few'
  return `perf_${String(((vu - 1) % 10000) + 1).padStart(5, '0')}`
}

export function login(username) {
  http.get(`${BASE_URL}/api/auth/csrf`, { tags: { name: 'GET /api/auth/csrf', kind: AUTH } })
  const response = send('POST', '/api/auth/login', JSON.stringify({ email: `${username}@example.com`, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /api/auth/login', kind: AUTH },
  })
  check(response, { 'ログインに成功': (r) => r.status === 200 })
  return response.status === 200
}

// テストを始める前に1回だけ、シードしたユーザーでログインできるかを確かめる。
// できなければ、バックエンドが性能テスト用の DB（raisetime_perf）で動いていないか、シードしていない
export function ensureSeeded() {
  const health = http.get(`${BASE_URL}/actuator/health`)
  if (health.status !== 200) {
    fail(`バックエンドに接続できません（${BASE_URL}）。perf/run.sh backend で起動してください`)
  }
  if (!login('perf_00001')) {
    fail('perf_00001 でログインできません。バックエンドを perf/run.sh backend で起動し、perf/run.sh seed でデータを入れてください')
  }
}

// ---------- API ----------

export function timeline(before) {
  const query = before ? `?before=${before}` : ''
  return json(request('GET', `/api/timeline${query}`, { name: before ? '/api/timeline?before' : '/api/timeline', kind: READ }))
}

export function hasNew(after) {
  return json(request('GET', `/api/timeline/has-new?after=${after}`, { name: '/api/timeline/has-new', kind: READ }))
}

export function postDetail(postId) {
  return json(request('GET', `/api/posts/${postId}`, { name: '/api/posts/{postId}', kind: READ }))
}

export function comments(postId) {
  return json(request('GET', `/api/posts/${postId}/comments`, { name: '/api/posts/{postId}/comments', kind: READ }))
}

export function userProfile(username) {
  return json(request('GET', `/api/users/${username}`, { name: '/api/users/{username}', kind: READ }))
}

export function userPosts(username) {
  return json(request('GET', `/api/users/${username}/posts`, { name: '/api/users/{username}/posts', kind: READ }))
}

export function searchUsers(keyword) {
  return json(request('GET', `/api/search/users?q=${encodeURIComponent(keyword)}`, { name: '/api/search/users', kind: READ }))
}

export function like(postId) {
  return json(request('PUT', `/api/posts/${postId}/like`, { name: '/api/posts/{postId}/like', kind: WRITE }))
}

export function unlike(postId) {
  return json(request('DELETE', `/api/posts/${postId}/like`, { name: '/api/posts/{postId}/like', kind: WRITE }))
}

export function createComment(postId, body) {
  return json(request('POST', `/api/posts/${postId}/comments`, {
    body: JSON.stringify({ body }),
    headers: { 'Content-Type': 'application/json' },
    name: '/api/posts/{postId}/comments',
    kind: WRITE,
  }))
}

// 投稿の作成は multipart/form-data（画像なし・本文だけ）。
// k6 はオブジェクトを渡すと application/x-www-form-urlencoded で送るため、multipart の本文を自分で組み立てる
export function createPost(body) {
  const boundary = `----k6perf${Date.now()}`
  const payload = `--${boundary}\r\nContent-Disposition: form-data; name="body"\r\n\r\n${body}\r\n--${boundary}--\r\n`
  return json(request('POST', '/api/posts', {
    body: payload,
    headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
    name: '/api/posts',
    kind: WRITE,
  }))
}
