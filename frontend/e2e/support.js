import { request as playwrightRequest } from '@playwright/test'
import { deflateSync } from 'node:zlib'

// E2E テストで共通に使う部品

export const PASSWORD = 'password123'
const BASE_URL = 'http://localhost:5174'

// テストごとに新しいユーザーを作る。@ユーザーID は e2e_ で始め、ほかのテスト・開発用のデータと重ならないようにする
// （@ユーザーID は 3〜15文字の半角英小文字・数字・_）
export function newUser(name = 'E2Eユーザー') {
  const id = Math.random().toString(36).slice(2, 10).padEnd(8, '0')
  const username = `e2e_${id}`
  return { name, username, email: `${username}@example.com`, password: PASSWORD }
}

// XSRF-TOKEN Cookie の値（POST などで X-XSRF-TOKEN ヘッダーに入れる）
async function csrfToken(context) {
  const cookies = await context.storageState()
  return cookies.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
}

// 画面と同じ API を、Cookie（ログイン状態・CSRF トークン）つきで呼ぶ。
// api は page.request（そのページと Cookie を共有する）か、別のユーザー用の apiAs() の戻り値
async function call(api, method, path, options = {}) {
  const headers = { 'X-XSRF-TOKEN': await csrfToken(api), ...options.headers }
  const response = await api.fetch(path, { method, ...options, headers })
  if (!response.ok()) {
    throw new Error(`${method} ${path} -> ${response.status()} ${await response.text()}`)
  }
  return response.status() === 204 ? null : response.json()
}

// 新規登録して、そのままログインした状態にする（画面を通さずに準備するとき用）
export async function signup(api, user) {
  await api.get('/api/auth/csrf')
  return call(api, 'POST', '/api/auth/signup', { data: user })
}

// 画面とは別の、もう1人のユーザーとして API を呼ぶための入れ物（Cookie は自分だけのもの）
export async function apiAs(user) {
  const api = await playwrightRequest.newContext({ baseURL: BASE_URL })
  await signup(api, user)
  return api
}

export function createPost(api, body) {
  return call(api, 'POST', '/api/posts', { multipart: { body } })
}

export function createComment(api, postId, body) {
  return call(api, 'POST', `/api/posts/${postId}/comments`, { data: { body } })
}

export function follow(api, username) {
  return call(api, 'PUT', `/api/users/${username}/follow`)
}

// 単色（上から下へ少し暗くなる）の PNG 画像。setFiles にそのまま渡せる形で返す
export function png(name, [r, g, b] = [90, 160, 240], width = 120, height = 80) {
  const rows = []
  for (let y = 0; y < height; y++) {
    const f = 1 - (0.35 * y) / height
    const pixel = Buffer.from([Math.round(r * f), Math.round(g * f), Math.round(b * f)])
    rows.push(Buffer.concat([Buffer.from([0]), ...Array(width).fill(pixel)]))
  }
  const chunk = (type, data) => {
    const length = Buffer.alloc(4)
    length.writeUInt32BE(data.length)
    const typeAndData = Buffer.concat([Buffer.from(type), data])
    const crc = Buffer.alloc(4)
    crc.writeUInt32BE(crc32(typeAndData))
    return Buffer.concat([length, typeAndData, crc])
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(width, 0)
  header.writeUInt32BE(height, 4)
  header.set([8, 2, 0, 0, 0], 8)
  const buffer = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.concat(rows))),
    chunk('IEND', Buffer.alloc(0)),
  ])
  return { name, mimeType: 'image/png', buffer }
}

function crc32(data) {
  let crc = 0xffffffff
  for (const byte of data) {
    crc ^= byte
    for (let k = 0; k < 8; k++) crc = crc & 1 ? (crc >>> 1) ^ 0xedb88320 : crc >>> 1
  }
  return (crc ^ 0xffffffff) >>> 0
}

// 次・前の部品へフォーカスを移すキー。Mac の Safari（WebKit）は、初期設定では Tab で入力欄にしか移らず、
// ボタン・リンクへは Option+Tab で移る（Safari の設定「Tab キーを押したときに Web ページ上の各項目を強調表示」をオンにすると Tab でも移る）
export function tabKey(browserName, { shift = false } = {}) {
  const tab = shift ? 'Shift+Tab' : 'Tab'
  return browserName === 'webkit' ? `Alt+${tab}` : tab
}

// ボタンを押して開くファイル選択に、ファイルを渡す（キーボードで押す場合は trigger に press を渡す）
export async function chooseFiles(page, trigger, files) {
  const [chooser] = await Promise.all([page.waitForEvent('filechooser'), trigger()])
  await chooser.setFiles(files)
}
