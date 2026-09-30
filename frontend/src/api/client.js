// バックエンド API を呼ぶための共通処理（docs/機能定義書/00_共通仕様.md）
// - ログイン状態（アクセストークン・リフレッシュトークン）は HttpOnly Cookie なので、ブラウザが自動で送る
// - GET 以外では、XSRF-TOKEN Cookie の値を X-XSRF-TOKEN ヘッダーで送る（CSRF 対策）
// - API が 401 を返したら、POST /api/auth/refresh でトークンを更新してから1回だけ呼び直す

const REFRESH_PATH = '/api/auth/refresh'

// 認証そのものの API は、401 でもトークンの更新をしない（ログイン失敗などで無限に更新しないため）
const NO_REFRESH_PATHS = new Set([
  '/api/auth/csrf',
  '/api/auth/signup',
  '/api/auth/login',
  REFRESH_PATH,
  '/api/auth/logout',
])

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `通信に失敗しました (status: ${status})`)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code
    // [{ field, message }]。入力エラー（400）や重複（409）のとき、項目ごとのメッセージが入る
    this.errors = body?.errors ?? []
  }
}

let unauthorizedHandler = () => {}
let refreshPromise = null

// トークンの更新にも失敗した（＝ログインし直しが必要）ときに呼ばれる処理を登録する
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

function readCookie(name) {
  const prefix = `${name}=`
  const found = document.cookie.split('; ').find((cookie) => cookie.startsWith(prefix))
  return found ? decodeURIComponent(found.slice(prefix.length)) : undefined
}

function send(path, { method = 'GET', body } = {}) {
  const headers = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const csrfToken = readCookie('XSRF-TOKEN')
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken
    }
  }
  return fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  })
}

// 同時に複数の API が 401 になっても、トークンの更新は1回にまとめる
// （リフレッシュトークンは使うたびに交換されるので、同じトークンで2回更新すると「再利用」と判定されてしまう）
function refreshTokens() {
  if (!refreshPromise) {
    refreshPromise = send(REFRESH_PATH, { method: 'POST' })
      .then((response) => response.ok)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

async function toApiError(response) {
  let body
  try {
    body = await response.json()
  } catch {
    body = undefined
  }
  return new ApiError(response.status, body)
}

export async function request(path, options = {}) {
  let response
  try {
    response = await send(path, options)
    if (response.status === 401 && !NO_REFRESH_PATHS.has(path)) {
      if (await refreshTokens()) {
        response = await send(path, options)
      } else {
        unauthorizedHandler()
      }
    }
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(0, { message: '通信に失敗しました。ネットワークを確認してください' })
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  return response.status === 204 ? undefined : response.json()
}
