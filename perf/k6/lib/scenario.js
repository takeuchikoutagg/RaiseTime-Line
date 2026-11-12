import { sleep } from 'k6'
import * as api from './api.js'

// 1人の利用者の動き（docs/パフォーマンステスト.md 4章）。smoke・load・stress で共通に使う。
// 1回の繰り返しが、タイムラインを開いてから少し使うまでの「1回の訪問」にあたる（20秒前後）

// VU ごとに1回だけログインする（BCrypt が重いので、繰り返しのたびにはログインしない）
let loggedInAs = null

// 人が画面を読む・考える時間（秒）
function think(min, max) {
  sleep(min + Math.random() * (max - min))
}

function chance(rate) {
  return Math.random() < rate
}

function pick(items) {
  return items[Math.floor(Math.random() * items.length)]
}

const SEARCH_WORDS = ['taro', 'hanako', 'sakura', 'perf_00', 'perf_0123', 'kenta 12']

export function visit() {
  const username = api.usernameForVu(__VU)
  if (loggedInAs !== username) {
    if (!api.login(username)) {
      think(5, 10)
      return
    }
    loggedInAs = username
  }

  // タイムラインを開く。取れなかったら、次の繰り返しでログインし直す（待たずに呼び続けて、負荷の形が崩れないように少し待つ）
  const first = api.timeline()
  if (!first || !first.items) {
    loggedInAs = null
    think(5, 10)
    return
  }
  think(1, 3)

  // 下までスクロールして、続きを読む
  let items = first.items
  if (first.nextCursor && chance(0.6)) {
    const next = api.timeline(first.nextCursor)
    if (next && next.items) items = items.concat(next.items)
    think(1, 3)
  }

  // 画面を開いている間の「新しい投稿があるか」の確認（画面は30秒ごと）
  if (first.items.length > 0) api.hasNew(first.items[0].id)

  // 始めたばかりの人は、タイムラインが空のことがある。検索して人を探してから帰る
  if (items.length === 0) {
    api.searchUsers(pick(SEARCH_WORDS))
    think(5, 10)
    return
  }
  const post = pick(items)

  // いいね／解除（押したあとに、もう一度押して元に戻すこともある）
  if (chance(0.3)) {
    if (post.likedByMe) api.unlike(post.id)
    else api.like(post.id)
    think(0.5, 1.5)
  }

  // 投稿の詳細とコメント一覧を開く
  if (chance(0.5)) {
    api.postDetail(post.id)
    api.comments(post.id)
    think(2, 4)
    if (chance(0.1)) {
      api.createComment(post.id, `性能テストのコメント（VU ${__VU}）`)
      think(1, 2)
    }
  }

  // 投稿した人のユーザーページを開く
  if (chance(0.2)) {
    api.userProfile(post.author.username)
    api.userPosts(post.author.username)
    think(2, 4)
  }

  // ユーザーを検索する
  if (chance(0.15)) {
    api.searchUsers(pick(SEARCH_WORDS))
    think(1, 3)
  }

  // ときどき投稿する
  if (chance(0.05)) {
    api.createPost(`性能テストの投稿（VU ${__VU}・${new Date().toISOString()}）`)
  }

  think(3, 8)
}

// 合格ライン（docs/パフォーマンステスト.md 2章）。
// 要件定義の「1回の表示で数百ミリ秒程度」から、読み取りは p95 300ms、書き込みは p95 500ms にする。
// API ごとの行は、終わったときの集計に API ごとの p95 を出すためのもの（判定の基準は同じ）
const READ_LIMIT = 'p(95)<300'
const WRITE_LIMIT = 'p(95)<500'

export const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  'http_req_duration{kind:read}': [READ_LIMIT],
  'http_req_duration{kind:write}': [WRITE_LIMIT],
  'http_req_duration{name:GET /api/timeline}': [READ_LIMIT],
  'http_req_duration{name:GET /api/timeline?before}': [READ_LIMIT],
  'http_req_duration{name:GET /api/timeline/has-new}': [READ_LIMIT],
  'http_req_duration{name:GET /api/posts/{postId}}': [READ_LIMIT],
  'http_req_duration{name:GET /api/posts/{postId}/comments}': [READ_LIMIT],
  'http_req_duration{name:GET /api/users/{username}}': [READ_LIMIT],
  'http_req_duration{name:GET /api/users/{username}/posts}': [READ_LIMIT],
  'http_req_duration{name:GET /api/search/users}': [READ_LIMIT],
  'http_req_duration{name:PUT /api/posts/{postId}/like}': [WRITE_LIMIT],
  'http_req_duration{name:DELETE /api/posts/{postId}/like}': [WRITE_LIMIT],
  'http_req_duration{name:POST /api/posts/{postId}/comments}': [WRITE_LIMIT],
  'http_req_duration{name:POST /api/posts}': [WRITE_LIMIT],
  'http_req_duration{name:POST /api/auth/login}': ['p(95)<2000'],
}

// smoke・load・stress で共通の設定
export const commonOptions = {
  // k6 は標準では、繰り返しのたびに Cookie を消す。ログイン状態（Cookie）を VU の間ずっと持ち続けるため、消さないようにする
  noCookiesReset: true,
  // 終わったときの集計に、p95 に加えて p99 も出す
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
}
