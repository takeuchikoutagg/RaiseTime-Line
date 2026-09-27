"use strict";

// RaiseTime Line プロトタイプ
// 仕様は docs/機能定義書/ を参照。DB の代わりに localStorage へ ER図と同じ形のデータを保存する。

const STORAGE_KEY = "raisetimeLineData";
const SESSION_KEY = "raisetimeLineSession";
const STORAGE_VERSION = 1;

const PAGE_SIZE = 20;
const MAX_BODY_LENGTH = 280;
const MAX_IMAGES = 4;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"];
const IMAGE_MAX_SIDE = 1024; // localStorage の容量（5MB前後）に収めるため、保存前に縮小する
const SEARCH_DEBOUNCE_MS = 300;
const USERNAME_PATTERN = /^[a-z0-9_]{3,15}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const SAMPLE_IMAGE = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='800' height='450'>" +
  "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>" +
  "<stop offset='0' stop-color='#1d9bf0'/><stop offset='1' stop-color='#7856ff'/></linearGradient></defs>" +
  "<rect width='800' height='450' fill='url(#g)'/>" +
  "<text x='400' y='245' font-size='56' text-anchor='middle' fill='white' font-family='monospace'>@RestController</text></svg>"
);

let state = loadState();
let currentUserId = loadSession();
let view = createViewState("");
let compose = null;              // S-04 モーダルの状態
let pendingDeletePostId = null;  // S-08 で削除する投稿
let searchTimer = null;

const postModal = document.getElementById("post-modal");
const postForm = document.getElementById("post-form");
const postModalTitle = document.getElementById("post-modal-title");
const postBody = document.getElementById("post-body");
const postImagePreview = document.getElementById("post-image-preview");
const postImageInput = document.getElementById("post-image-input");
const postCounter = document.getElementById("post-counter");
const postError = document.getElementById("post-error");
const postSubmit = document.getElementById("post-submit");
const deleteModal = document.getElementById("delete-modal");

// ---------- データ層 ----------

function createDefaultState() {
  const now = Date.now();
  const ago = (minutes) => new Date(now - minutes * 60 * 1000).toISOString();
  const DAY = 60 * 24;

  return {
    version: STORAGE_VERSION,
    nextId: 100,
    users: [
      { id: 1, username: "taro_dev", name: "山田太郎", email: "taro@example.com", password: "password123", createdAt: ago(30 * DAY) },
      { id: 2, username: "hanako", name: "佐藤花子", email: "hanako@example.com", password: "password123", createdAt: ago(20 * DAY) },
      { id: 3, username: "ichiro", name: "鈴木一郎", email: "ichiro@example.com", password: "password123", createdAt: ago(10 * DAY) },
      { id: 4, username: "s_taro", name: "Taro S.", email: "s_taro@example.com", password: "password123", createdAt: ago(5 * DAY) },
    ],
    posts: [
      { id: 5, userId: 1, body: "Spring BootでAPIを作ってみた！\nControllerとServiceの分け方が少しわかってきた。", images: [SAMPLE_IMAGE], createdAt: ago(3), editedAt: null },
      { id: 6, userId: 2, body: "ER図むずかしい…多対多は中間テーブルを作る、と覚えた。", images: [], createdAt: ago(60), editedAt: ago(50) },
      { id: 7, userId: 3, body: "今日はReactのuseEffectを勉強。依存配列を空にすると最初の1回だけ動く。", images: [], createdAt: ago(120), editedAt: null },
      { id: 8, userId: 1, body: "朝活で1時間勉強できた☀️", images: [], createdAt: ago(5 * 60), editedAt: null },
      { id: 9, userId: 2, body: "PostgreSQLのインデックス、EXPLAINで確認すると効いているのがわかって楽しい", images: [], createdAt: ago(26 * 60), editedAt: null },
      { id: 10, userId: 4, body: "はじめまして！Javaの勉強を始めました。", images: [], createdAt: ago(30 * 60), editedAt: null },
      { id: 11, userId: 3, body: "Dockerでローカル環境を作った。docker compose up だけでDBが立ち上がるのは便利", images: [], createdAt: ago(2 * DAY), editedAt: null },
    ],
    comments: [
      { id: 12, postId: 5, userId: 2, body: "おつかれさま！", createdAt: ago(2) },
      { id: 13, postId: 5, userId: 3, body: "自分も明日やってみます", createdAt: ago(1) },
      { id: 14, postId: 6, userId: 1, body: "中間テーブル大事ですよね", createdAt: ago(40) },
    ],
    likes: [
      { userId: 2, postId: 5 },
      { userId: 3, postId: 5 },
      { userId: 1, postId: 6 },
      { userId: 1, postId: 9 },
    ],
    follows: [
      { followerId: 1, followeeId: 2 },
      { followerId: 2, followeeId: 1 },
      { followerId: 3, followeeId: 1 },
      { followerId: 4, followeeId: 1 },
    ],
  };
}

function loadState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return createDefaultState();
    const parsed = JSON.parse(raw);
    if (!parsed || parsed.version !== STORAGE_VERSION) return createDefaultState();
    return parsed;
  } catch (e) {
    console.error("localStorageの読み込みに失敗しました", e);
    return createDefaultState();
  }
}

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    return true;
  } catch (e) {
    console.error("localStorageへの保存に失敗しました", e);
    alert("保存容量が足りません。画像を減らすか、画面下の「データを初期化」を押してください。");
    return false;
  }
}

// state を変更して保存する。保存に失敗したら変更前に戻す（DB のトランザクションの代わり）
function commit(mutate) {
  const snapshot = JSON.stringify(state);
  mutate();
  if (saveState()) return true;
  state = JSON.parse(snapshot);
  return false;
}

function nextId() {
  return state.nextId++;
}

function loadSession() {
  const id = Number(localStorage.getItem(SESSION_KEY));
  return state.users.some((u) => u.id === id) ? id : null;
}

function login(userId) {
  currentUserId = userId;
  localStorage.setItem(SESSION_KEY, String(userId));
}

function logout() {
  currentUserId = null;
  localStorage.removeItem(SESSION_KEY);
}

// ---------- 参照用のヘルパー ----------

function currentUser() {
  return state.users.find((u) => u.id === currentUserId) ?? null;
}

function findUserById(id) {
  return state.users.find((u) => u.id === id) ?? null;
}

function findUserByUsername(username) {
  return state.users.find((u) => u.username === username) ?? null;
}

function findPost(id) {
  return state.posts.find((p) => p.id === id) ?? null;
}

function isFollowing(followerId, followeeId) {
  return state.follows.some((f) => f.followerId === followerId && f.followeeId === followeeId);
}

function isLikedBy(userId, postId) {
  return state.likes.some((l) => l.userId === userId && l.postId === postId);
}

function likeCountOf(postId) {
  return state.likes.filter((l) => l.postId === postId).length;
}

function commentCountOf(postId) {
  return state.comments.filter((c) => c.postId === postId).length;
}

function byNewest(a, b) {
  return b.createdAt.localeCompare(a.createdAt) || b.id - a.id;
}

function byOldest(a, b) {
  return a.createdAt.localeCompare(b.createdAt) || a.id - b.id;
}

// 絵文字も1文字と数える（String.length だと絵文字が2文字になる）
function countChars(value) {
  return [...value].length;
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[ch]));
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function formatDateTime(iso) {
  const d = new Date(iso);
  return `${d.getFullYear()}/${pad2(d.getMonth() + 1)}/${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

function relativeTime(iso) {
  const diffSec = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diffSec < 60) return "たった今";
  if (diffSec < 60 * 60) return `${Math.floor(diffSec / 60)}分前`;
  if (diffSec < 60 * 60 * 24) return `${Math.floor(diffSec / 3600)}時間前`;
  if (diffSec < 60 * 60 * 24 * 7) return `${Math.floor(diffSec / 86400)}日前`;
  return formatDateTime(iso).slice(0, 10);
}

function navigate(hash) {
  if (location.hash === hash) {
    render();
  } else {
    location.hash = hash;
  }
}

// ---------- ルーティング ----------

function createViewState(key) {
  return {
    key,
    timelineLimit: PAGE_SIZE,
    userPostsLimit: PAGE_SIZE,
    commentsLimit: PAGE_SIZE,
    searchQuery: "",
    searchLimit: PAGE_SIZE,
  };
}

function parseRoute() {
  const parts = location.hash.replace(/^#/, "").split("/").filter(Boolean);
  if (parts.length === 0) return { name: "timeline" };
  if (parts[0] === "login" && parts.length === 1) return { name: "login" };
  if (parts[0] === "signup" && parts.length === 1) return { name: "signup" };
  if (parts[0] === "search" && parts.length === 1) return { name: "search" };
  if (parts[0] === "posts" && parts.length === 2) return { name: "post", id: Number(parts[1]) };
  if (parts[0] === "users" && parts.length === 2) return { name: "user", username: decodeURIComponent(parts[1]) };
  return { name: "notFound" };
}

function render() {
  const route = parseRoute();
  const me = currentUser();
  const isPublic = route.name === "login" || route.name === "signup";

  // ログインしていなければログイン画面へ、ログイン中にログイン・新規登録画面を開いたらタイムラインへ
  if (!me && !isPublic) {
    location.replace("#/login");
    return;
  }
  if (me && isPublic) {
    location.replace("#/");
    return;
  }

  const key = location.hash || "#/";
  const isNewPage = view.key !== key;
  if (isNewPage) {
    view = createViewState(key);
    window.scrollTo(0, 0);
  }

  updateNav(route, me);

  // 検索画面は入力欄のフォーカスを保つため、結果欄だけ描き直す
  if (route.name === "search" && !isNewPage && document.getElementById("search-input")) {
    renderSearchResults();
    return;
  }

  const app = document.getElementById("app");
  switch (route.name) {
    case "login": app.innerHTML = loginView(); break;
    case "signup": app.innerHTML = signupView(); break;
    case "timeline": app.innerHTML = timelineView(me); break;
    case "post": app.innerHTML = postDetailView(route.id); break;
    case "user": app.innerHTML = userPageView(route.username); break;
    case "search":
      app.innerHTML = searchView();
      renderSearchResults();
      break;
    default: app.innerHTML = notFoundView();
  }
}

function updateNav(route, me) {
  const nav = document.getElementById("app-nav");
  nav.hidden = !me;
  document.body.classList.toggle("is-logged-in", Boolean(me));
  if (!me) return;

  document.getElementById("nav-mypage").href = `#/users/${encodeURIComponent(me.username)}`;
  const active =
    route.name === "timeline" ? "timeline" :
    route.name === "search" ? "search" :
    route.name === "user" && route.username === me.username ? "mypage" : "";
  nav.querySelectorAll("[data-nav]").forEach((link) => {
    link.classList.toggle("is-active", link.dataset.nav === active);
  });
}

// ---------- 部品 ----------

function avatarHtml(user, extraClass = "") {
  let hash = 0;
  for (const ch of user.username) hash = (hash * 31 + ch.codePointAt(0)) % 360;
  const initial = [...user.name][0] ?? "?";
  return `<a href="#/users/${encodeURIComponent(user.username)}" class="avatar ${extraClass}" style="background: hsl(${hash}, 55%, 50%)" aria-label="${escapeHtml(user.name)}のページ">${escapeHtml(initial)}</a>`;
}

function userLinkHtml(user) {
  const href = `#/users/${encodeURIComponent(user.username)}`;
  return `<a href="${href}" class="post__name">${escapeHtml(user.name)}</a><a href="${href}" class="post__username">@${escapeHtml(user.username)}</a>`;
}

function followButtonHtml(user) {
  const me = currentUser();
  if (isFollowing(me.id, user.id)) {
    return `<button type="button" class="btn btn-follow is-following" data-action="toggle-follow" data-user-id="${user.id}">
      <span class="btn-follow__default">フォロー中</span><span class="btn-follow__hover">フォロー解除</span>
    </button>`;
  }
  return `<button type="button" class="btn btn--primary btn-follow" data-action="toggle-follow" data-user-id="${user.id}">フォローする</button>`;
}

function imagesHtml(images) {
  if (!images.length) return "";
  const imgs = images.map((src, i) => `<img src="${escapeHtml(src)}" alt="添付画像${i + 1}" loading="lazy">`).join("");
  return `<div class="post__images post__images--${images.length}">${imgs}</div>`;
}

// 投稿1件（タイムライン・投稿詳細・ユーザーページで共通）。インプレッション数・リツイートボタンは置かない
function postHtml(post, { detail = false } = {}) {
  const me = currentUser();
  const author = findUserById(post.userId);
  const liked = isLikedBy(me.id, post.id);
  const isMine = post.userId === me.id;
  const detailHref = `#/posts/${post.id}`;

  const time = detail ? formatDateTime(post.createdAt) : relativeTime(post.createdAt);
  const edited = post.editedAt ? `<span class="post__edited">· 編集済み</span>` : "";
  const body = `<p class="post__body">${escapeHtml(post.body)}</p>`;

  const menu = isMine ? `
    <div class="post__menu">
      <button type="button" class="icon-btn" data-action="toggle-menu" aria-label="メニュー">…</button>
      <div class="menu" hidden>
        <button type="button" class="menu__item" data-action="edit-post" data-post-id="${post.id}">編集</button>
        <button type="button" class="menu__item menu__item--danger" data-action="delete-post" data-post-id="${post.id}">削除</button>
      </div>
    </div>` : "";

  return `
    <article class="post ${detail ? "post--detail" : "post--clickable"}">
      ${avatarHtml(author)}
      <div class="post__main">
        <div class="post__header">
          ${userLinkHtml(author)}
          <span>·</span>
          <span title="${escapeHtml(formatDateTime(post.createdAt))}">${escapeHtml(time)}</span>
          ${edited}
        </div>
        ${detail ? body : `<a href="${detailHref}" class="post__body-link">${body}</a>`}
        ${imagesHtml(post.images)}
        ${detail && post.editedAt ? `<div class="post__meta">編集済み ${escapeHtml(formatDateTime(post.editedAt))}</div>` : ""}
        <div class="post__actions">
          <button type="button" class="action action--like ${liked ? "is-liked" : ""}" data-action="toggle-like" data-post-id="${post.id}" aria-pressed="${liked}" aria-label="いいね">
            <span aria-hidden="true">${liked ? "♥" : "♡"}</span> ${likeCountOf(post.id)}
          </button>
          <a href="${detailHref}" class="action" aria-label="コメント">
            <span aria-hidden="true">💬</span> ${commentCountOf(post.id)}
          </a>
        </div>
      </div>
      ${menu}
    </article>`;
}

function postListHtml(posts, limit, moreAction) {
  const items = posts.slice(0, limit).map((p) => postHtml(p)).join("");
  const more = posts.length > limit
    ? `<button type="button" class="btn btn--more" data-action="${moreAction}">もっと見る</button>`
    : "";
  return items + more;
}

// ---------- 画面 ----------

// S-01 ログイン（F-02）
function loginView() {
  return `
    <section class="auth">
      <h1 class="auth__title">ログイン</h1>
      <form id="login-form" novalidate>
        <div class="field">
          <label class="field__label" for="login-email">メールアドレス</label>
          <input class="input" type="email" id="login-email" name="email" autocomplete="email">
        </div>
        <div class="field">
          <label class="field__label" for="login-password">パスワード</label>
          <input class="input" type="password" id="login-password" name="password" autocomplete="current-password">
        </div>
        <p class="form-error" id="login-error" role="alert"></p>
        <button type="submit" class="btn btn--primary btn--block">ログイン</button>
      </form>
      <p class="auth__switch">アカウントをお持ちでない方は <a href="#/signup">新規登録</a></p>
      <div class="demo-box">
        デモ用アカウント（パスワードはすべて <code>password123</code>）<br>
        <code>taro@example.com</code> ／ <code>hanako@example.com</code> ／ <code>ichiro@example.com</code>
      </div>
    </section>`;
}

// S-02 新規登録（F-01）
function signupView() {
  const field = (name, label, input, hint = "") => `
    <div class="field">
      <label class="field__label" for="signup-${name}">${label}${hint ? ` <span class="field__hint">${hint}</span>` : ""}</label>
      ${input}
      <p class="field-error" data-error-for="${name}"></p>
    </div>`;

  return `
    <section class="auth">
      <h1 class="auth__title">アカウント作成</h1>
      <form id="signup-form" novalidate>
        ${field("name", "名前", `<input class="input" id="signup-name" name="name" autocomplete="name">`, "（1〜50文字）")}
        ${field("username", "@ユーザーID",
          `<div class="input-prefix"><span class="input-prefix__mark">@</span><input class="input" id="signup-username" name="username" autocomplete="username" autocapitalize="off"></div>`,
          "（3〜15文字・半角英小文字・数字・_。あとから変更できません）")}
        ${field("email", "メールアドレス", `<input class="input" type="email" id="signup-email" name="email" autocomplete="email">`)}
        ${field("password", "パスワード", `<input class="input" type="password" id="signup-password" name="password" autocomplete="new-password">`, "（8〜64文字）")}
        ${field("passwordConfirm", "パスワード（確認）", `<input class="input" type="password" id="signup-passwordConfirm" name="passwordConfirm" autocomplete="new-password">`)}
        <button type="submit" class="btn btn--primary btn--block">登録する</button>
      </form>
      <p class="auth__switch">すでにアカウントをお持ちの方は <a href="#/login">ログイン</a></p>
    </section>`;
}

// S-03 タイムライン（F-03）
function timelineView(me) {
  const visibleUserIds = new Set([
    me.id,
    ...state.follows.filter((f) => f.followerId === me.id).map((f) => f.followeeId),
  ]);
  const posts = state.posts.filter((p) => visibleUserIds.has(p.userId)).sort(byNewest);

  const list = posts.length
    ? postListHtml(posts, view.timelineLimit, "more-timeline")
    : `<div class="empty">ユーザーを検索してフォローすると、ここに投稿が表示されます<br><br><a href="#/search" class="btn btn--primary">ユーザーを探す</a></div>`;

  return `
    <h1 class="page-title">ホーム</h1>
    <div class="compose-bar">
      ${avatarHtml(me)}
      <button type="button" class="compose-bar__input" data-action="open-compose">いまどうしてる？</button>
    </div>
    ${list}`;
}

// S-05 投稿詳細（F-05）
function postDetailView(postId) {
  const post = findPost(postId);
  if (!post) {
    return `
      <h1 class="page-title">投稿</h1>
      <div class="empty">投稿が見つかりません<br><br><a href="#/">タイムラインへ戻る</a></div>`;
  }

  const comments = state.comments.filter((c) => c.postId === post.id).sort(byOldest);
  const commentItems = comments.length
    ? comments.slice(0, view.commentsLimit).map(commentHtml).join("")
    : `<div class="empty">まだコメントはありません</div>`;
  const more = comments.length > view.commentsLimit
    ? `<button type="button" class="btn btn--more" data-action="more-comments">もっと見る</button>`
    : "";

  return `
    <button type="button" class="icon-btn back-link" data-action="go-back">← 戻る</button>
    ${postHtml(post, { detail: true })}
    <form id="comment-form" class="comment-form" data-post-id="${post.id}" novalidate>
      <textarea class="textarea" name="body" rows="2" placeholder="コメントを書く" aria-label="コメント"></textarea>
      <div class="comment-form__footer">
        <span class="counter" id="comment-counter">0 / ${MAX_BODY_LENGTH}</span>
        <button type="submit" class="btn btn--primary btn--small">送信</button>
      </div>
      <p class="form-error" id="comment-error" role="alert"></p>
    </form>
    ${commentItems}
    ${more}`;
}

function commentHtml(comment) {
  const author = findUserById(comment.userId);
  return `
    <div class="comment">
      ${avatarHtml(author)}
      <div class="post__main">
        <div class="post__header">
          ${userLinkHtml(author)}
          <span>·</span>
          <span title="${escapeHtml(formatDateTime(comment.createdAt))}">${escapeHtml(relativeTime(comment.createdAt))}</span>
        </div>
        <p class="post__body">${escapeHtml(comment.body)}</p>
      </div>
    </div>`;
}

// S-06 ユーザーページ（F-08）
function userPageView(username) {
  const user = findUserByUsername(username);
  if (!user) {
    return `
      <h1 class="page-title">ユーザー</h1>
      <div class="empty">ユーザーが見つかりません</div>`;
  }

  const me = currentUser();
  const isMe = user.id === me.id;
  const followingCount = state.follows.filter((f) => f.followerId === user.id).length;
  const followerCount = state.follows.filter((f) => f.followeeId === user.id).length;
  const posts = state.posts.filter((p) => p.userId === user.id).sort(byNewest);

  return `
    <h1 class="page-title">${escapeHtml(user.name)}</h1>
    <section class="profile">
      <div class="profile__top">
        ${avatarHtml(user, "avatar--large")}
        ${isMe ? "" : followButtonHtml(user)}
      </div>
      <h2 class="profile__name">${escapeHtml(user.name)}</h2>
      <div class="profile__username">@${escapeHtml(user.username)}</div>
      <div class="profile__stats">
        <span><strong>${followingCount}</strong> フォロー</span>
        <span><strong>${followerCount}</strong> フォロワー</span>
      </div>
    </section>
    ${posts.length ? postListHtml(posts, view.userPostsLimit, "more-user-posts") : `<div class="empty">まだ投稿がありません</div>`}`;
}

// S-07 ユーザー検索（F-09）
function searchView() {
  return `
    <h1 class="page-title">ユーザー検索</h1>
    <div class="search-box">
      <input type="search" class="input" id="search-input" value="${escapeHtml(view.searchQuery)}"
        placeholder="🔍 @ユーザーID または 名前で検索" aria-label="ユーザー検索" autocomplete="off" autocapitalize="off">
    </div>
    <div id="search-results"></div>`;
}

// 先頭の @ を除き、大文字小文字を区別せずに @ユーザーID と名前を部分一致で探す。
// 並び順は「@ユーザーIDの完全一致 → 前方一致 → その他」、同じ順位は @ユーザーID の昇順
function searchUsers(rawQuery) {
  const query = rawQuery.trim().replace(/^@/, "").toLowerCase();
  if (!query) return [];
  const rank = (u) => (u.username === query ? 0 : u.username.startsWith(query) ? 1 : 2);
  return state.users
    .filter((u) => u.username.includes(query) || u.name.toLowerCase().includes(query))
    .sort((a, b) => rank(a) - rank(b) || a.username.localeCompare(b.username));
}

function renderSearchResults() {
  const container = document.getElementById("search-results");
  if (!container) return;
  const query = view.searchQuery.trim().replace(/^@/, "");

  if (!query) {
    container.innerHTML = `<div class="empty">@ユーザーID または名前で、フォローしたいユーザーを探せます</div>`;
    return;
  }
  if (countChars(query) > 50) {
    container.innerHTML = `<div class="empty">キーワードは50文字以内で入力してください</div>`;
    return;
  }

  const results = searchUsers(view.searchQuery);
  if (!results.length) {
    container.innerHTML = `<div class="empty">該当するユーザーが見つかりません</div>`;
    return;
  }

  const me = currentUser();
  const rows = results.slice(0, view.searchLimit).map((user) => `
    <div class="user-row">
      ${avatarHtml(user)}
      <div class="user-row__info">
        <a href="#/users/${encodeURIComponent(user.username)}" class="user-row__name">${escapeHtml(user.name)}</a>
        <span class="user-row__username">@${escapeHtml(user.username)}</span>
      </div>
      ${user.id === me.id ? `<span class="badge-me">あなた</span>` : followButtonHtml(user)}
    </div>`).join("");
  const more = results.length > view.searchLimit
    ? `<button type="button" class="btn btn--more" data-action="more-search">もっと見る</button>`
    : "";
  container.innerHTML = rows + more;
}

function notFoundView() {
  return `
    <h1 class="page-title">ページが見つかりません</h1>
    <div class="empty"><a href="#/">タイムラインへ戻る</a></div>`;
}

// ---------- F-01 新規登録 / F-02 ログイン・ログアウト ----------

function handleLogin(form) {
  const email = form.elements.email.value.trim().toLowerCase();
  const password = form.elements.password.value;
  const error = document.getElementById("login-error");

  if (!email || !password) {
    error.textContent = "メールアドレスとパスワードを入力してください";
    return;
  }
  // プロトタイプのためパスワードを平文で照合している。本番は BCrypt で照合する
  const user = state.users.find((u) => u.email === email && u.password === password);
  if (!user) {
    error.textContent = "メールアドレスまたはパスワードが正しくありません";
    return;
  }
  login(user.id);
  navigate("#/");
}

function validateSignup(values) {
  const errors = {};
  const nameLength = countChars(values.name.trim());
  if (nameLength < 1 || nameLength > 50) {
    errors.name = "名前は1〜50文字で入力してください";
  }
  if (!USERNAME_PATTERN.test(values.username)) {
    errors.username = "@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください";
  } else if (findUserByUsername(values.username)) {
    errors.username = "この@ユーザーIDはすでに使われています";
  }
  if (!EMAIL_PATTERN.test(values.email) || values.email.length > 255) {
    errors.email = "メールアドレスの形式で入力してください";
  } else if (state.users.some((u) => u.email === values.email)) {
    errors.email = "このメールアドレスはすでに登録されています";
  }
  const passwordLength = countChars(values.password);
  if (passwordLength < 8 || passwordLength > 64) {
    errors.password = "パスワードは8〜64文字で入力してください";
  }
  if (values.passwordConfirm !== values.password) {
    errors.passwordConfirm = "パスワード（確認）がパスワードと一致しません";
  }
  return errors;
}

function handleSignup(form) {
  const values = {
    name: form.elements.name.value,
    username: form.elements.username.value.trim().replace(/^@/, ""),
    email: form.elements.email.value.trim().toLowerCase(), // 小文字にそろえてから保存・照合する
    password: form.elements.password.value,
    passwordConfirm: form.elements.passwordConfirm.value,
  };
  const errors = validateSignup(values);

  form.querySelectorAll("[data-error-for]").forEach((el) => {
    const name = el.dataset.errorFor;
    el.textContent = errors[name] ?? "";
    form.elements[name].classList.toggle("is-invalid", Boolean(errors[name]));
  });
  if (Object.keys(errors).length) return;

  let newUserId = null;
  const ok = commit(() => {
    newUserId = nextId();
    state.users.push({
      id: newUserId,
      username: values.username,
      name: values.name.trim(),
      email: values.email,
      password: values.password, // プロトタイプのため平文。本番は BCrypt でハッシュ化する
      createdAt: new Date().toISOString(),
    });
  });
  if (!ok) return;
  login(newUserId); // 登録したらそのままログインした状態にする
  navigate("#/");
}

// ---------- F-04 投稿作成 / F-10 投稿編集（S-04 モーダル） ----------

function openComposeModal(postId = null) {
  const me = currentUser();
  if (postId) {
    const post = findPost(postId);
    if (!post || post.userId !== me.id) {
      alert("この投稿は編集できません");
      return;
    }
    compose = { mode: "edit", postId, images: [...post.images], originalBody: post.body, originalImages: [...post.images] };
    postBody.value = post.body;
    postModalTitle.textContent = "投稿を編集";
    postSubmit.textContent = "保存する";
  } else {
    compose = { mode: "create", postId: null, images: [], originalBody: "", originalImages: [] };
    postBody.value = "";
    postModalTitle.textContent = "新しい投稿";
    postSubmit.textContent = "投稿する";
  }
  postError.textContent = "";
  renderComposePreview();
  updateComposeState();
  postModal.showModal();
  postBody.focus();
}

function isComposeDirty() {
  return postBody.value !== compose.originalBody
    || compose.images.length !== compose.originalImages.length
    || compose.images.some((src, i) => src !== compose.originalImages[i]);
}

function closeComposeModal() {
  if (compose && compose.mode === "edit" && isComposeDirty() && !confirm("変更を破棄しますか？")) return;
  postModal.close();
}

function renderComposePreview() {
  postImagePreview.innerHTML = compose.images.map((src, i) => `
    <div class="image-preview__item">
      <img src="${escapeHtml(src)}" alt="選択した画像${i + 1}">
      <button type="button" class="image-preview__remove" data-action="remove-image" data-index="${i}" aria-label="画像${i + 1}を外す">×</button>
    </div>`).join("");
}

function updateComposeState() {
  if (!compose) return;
  const length = countChars(postBody.value);
  postCounter.textContent = `${length} / ${MAX_BODY_LENGTH}`;
  postCounter.classList.toggle("is-over", length > MAX_BODY_LENGTH);

  const valid = postBody.value.trim() !== "" && length <= MAX_BODY_LENGTH;
  const unchanged = compose.mode === "edit" && !isComposeDirty();
  postSubmit.disabled = !valid || unchanged;
}

// 画像を縮小して data URL にする。読み込めない（中身が画像でない）ファイルはここで弾かれる
function resizeImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, IMAGE_MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
      const width = Math.round(img.naturalWidth * scale);
      const height = Math.round(img.naturalHeight * scale);
      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#ffffff"; // 透過PNGの背景が黒くならないようにする
      ctx.fillRect(0, 0, width, height);
      ctx.drawImage(img, 0, 0, width, height);
      URL.revokeObjectURL(url);
      resolve(canvas.toDataURL("image/jpeg", 0.85));
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("画像を読み込めませんでした"));
    };
    img.src = url;
  });
}

async function addImages(files) {
  postError.textContent = "";
  for (const file of files) {
    if (!compose) return;
    if (compose.images.length >= MAX_IMAGES) {
      postError.textContent = "画像は4枚まで添付できます";
      break;
    }
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      postError.textContent = "JPEG・PNG・GIF・WebP の画像を選んでください";
      continue;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      postError.textContent = "画像は1枚5MBまでです";
      continue;
    }
    try {
      const dataUrl = await resizeImage(file);
      if (!compose) return;
      compose.images.push(dataUrl);
    } catch (e) {
      postError.textContent = "画像を読み込めませんでした";
    }
  }
  renderComposePreview();
  updateComposeState();
}

function submitCompose() {
  if (!compose) return;
  const me = currentUser();
  const body = postBody.value;
  const length = countChars(body);
  if (body.trim() === "" || length > MAX_BODY_LENGTH) {
    postError.textContent = "本文は1〜280文字で入力してください";
    return;
  }
  if (compose.images.length > MAX_IMAGES) {
    postError.textContent = "画像は4枚まで添付できます";
    return;
  }

  postSubmit.disabled = true; // 2重送信を防ぐ
  const images = [...compose.images];
  let ok;

  if (compose.mode === "create") {
    ok = commit(() => {
      state.posts.push({ id: nextId(), userId: me.id, body, images, createdAt: new Date().toISOString(), editedAt: null });
    });
  } else {
    const post = findPost(compose.postId);
    if (!post || post.userId !== me.id) {
      postError.textContent = post ? "この投稿は編集できません" : "投稿が見つかりません";
      updateComposeState();
      return;
    }
    // 投稿日時は変えない（タイムラインの並び順も変わらない）。edited_at だけ更新する
    ok = commit(() => {
      const target = findPost(compose.postId);
      target.body = body;
      target.images = images;
      target.editedAt = new Date().toISOString();
    });
  }

  if (!ok) {
    updateComposeState();
    return;
  }
  const wasCreate = compose.mode === "create";
  postModal.close();
  if (wasCreate) {
    navigate("#/"); // 作成したらタイムラインの先頭に表示する
    window.scrollTo(0, 0);
  } else {
    render();
  }
}

// ---------- F-05 コメント ----------

function handleComment(form) {
  const postId = Number(form.dataset.postId);
  const body = form.elements.body.value;
  const error = document.getElementById("comment-error");
  const length = countChars(body);

  if (body.trim() === "" || length > MAX_BODY_LENGTH) {
    error.textContent = "コメントは1〜280文字で入力してください";
    return;
  }
  if (!findPost(postId)) {
    error.textContent = "投稿が見つかりません";
    return;
  }
  const ok = commit(() => {
    state.comments.push({ id: nextId(), postId, userId: currentUser().id, body, createdAt: new Date().toISOString() });
  });
  if (!ok) return;
  // 追加したコメントは一覧の末尾に出るので、全件見えるようにする
  view.commentsLimit = Math.max(view.commentsLimit, commentCountOf(postId));
  render();
}

// ---------- F-06 いいね / F-07 フォロー ----------

function toggleLike(postId) {
  const me = currentUser();
  if (!findPost(postId)) {
    alert("投稿が見つかりません");
    render();
    return;
  }
  // 1人1投稿につき1回まで。押すたびに いいね ⇔ 解除 を切り替える
  commit(() => {
    const index = state.likes.findIndex((l) => l.userId === me.id && l.postId === postId);
    if (index >= 0) {
      state.likes.splice(index, 1);
    } else {
      state.likes.push({ userId: me.id, postId });
    }
  });
  render();
}

function toggleFollow(userId) {
  const me = currentUser();
  if (userId === me.id) {
    alert("自分自身はフォローできません");
    return;
  }
  if (!findUserById(userId)) {
    alert("ユーザーが見つかりません");
    return;
  }
  commit(() => {
    const index = state.follows.findIndex((f) => f.followerId === me.id && f.followeeId === userId);
    if (index >= 0) {
      state.follows.splice(index, 1);
    } else {
      state.follows.push({ followerId: me.id, followeeId: userId });
    }
  });
  render();
}

// ---------- F-11 投稿削除（S-08 モーダル） ----------

function openDeleteModal(postId) {
  pendingDeletePostId = postId;
  deleteModal.showModal();
}

function deletePost() {
  const postId = pendingDeletePostId;
  const me = currentUser();
  const post = findPost(postId);
  deleteModal.close();

  if (!post) {
    alert("投稿が見つかりません");
    render();
    return;
  }
  if (post.userId !== me.id) {
    alert("この投稿は削除できません");
    return;
  }
  // DB の ON DELETE CASCADE と同じく、コメント・いいねも一緒に消す
  const ok = commit(() => {
    state.posts = state.posts.filter((p) => p.id !== postId);
    state.comments = state.comments.filter((c) => c.postId !== postId);
    state.likes = state.likes.filter((l) => l.postId !== postId);
  });
  if (!ok) return;

  const route = parseRoute();
  if (route.name === "post" && route.id === postId) {
    navigate("#/"); // 投稿詳細で削除したらタイムラインへ戻る
  } else {
    render();
  }
}

// ---------- イベント ----------

function closeAllMenus(except = null) {
  document.querySelectorAll(".post__menu .menu").forEach((menu) => {
    if (menu !== except) menu.hidden = true;
  });
}

document.addEventListener("click", (e) => {
  if (!e.target.closest(".post__menu")) closeAllMenus();

  const el = e.target.closest("[data-action]");
  if (!el) return;
  const postId = Number(el.dataset.postId);
  const userId = Number(el.dataset.userId);

  switch (el.dataset.action) {
    case "logout":
      logout();
      navigate("#/login");
      break;
    case "open-compose":
      openComposeModal();
      break;
    case "close-post-modal":
      closeComposeModal();
      break;
    case "remove-image":
      compose.images.splice(Number(el.dataset.index), 1);
      renderComposePreview();
      updateComposeState();
      break;
    case "toggle-menu": {
      const menu = el.nextElementSibling;
      closeAllMenus(menu);
      menu.hidden = !menu.hidden;
      break;
    }
    case "edit-post":
      closeAllMenus();
      openComposeModal(postId);
      break;
    case "delete-post":
      closeAllMenus();
      openDeleteModal(postId);
      break;
    case "close-delete-modal":
      deleteModal.close();
      break;
    case "confirm-delete":
      deletePost();
      break;
    case "toggle-like":
      toggleLike(postId);
      break;
    case "toggle-follow":
      toggleFollow(userId);
      break;
    case "more-timeline":
      view.timelineLimit += PAGE_SIZE;
      render();
      break;
    case "more-user-posts":
      view.userPostsLimit += PAGE_SIZE;
      render();
      break;
    case "more-comments":
      view.commentsLimit += PAGE_SIZE;
      render();
      break;
    case "more-search":
      view.searchLimit += PAGE_SIZE;
      renderSearchResults();
      break;
    case "go-back":
      if (history.length > 1) {
        history.back();
      } else {
        navigate("#/");
      }
      break;
    case "reset-data":
      if (!confirm("すべてのデータを初期状態に戻します。よろしいですか？")) return;
      state = createDefaultState();
      saveState();
      logout();
      navigate("#/login");
      break;
  }
});

document.addEventListener("submit", (e) => {
  const form = e.target;
  if (form.id === "login-form") {
    e.preventDefault();
    handleLogin(form);
  } else if (form.id === "signup-form") {
    e.preventDefault();
    handleSignup(form);
  } else if (form.id === "comment-form") {
    e.preventDefault();
    handleComment(form);
  } else if (form.id === "post-form") {
    e.preventDefault();
    submitCompose();
  }
});

document.addEventListener("input", (e) => {
  const target = e.target;

  if (target.id === "search-input") {
    // 入力が300ミリ秒止まってから検索する（デバウンス）
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      view.searchQuery = target.value;
      view.searchLimit = PAGE_SIZE;
      renderSearchResults();
    }, SEARCH_DEBOUNCE_MS);
  } else if (target.name === "body" && target.form?.id === "comment-form") {
    const length = countChars(target.value);
    const counter = document.getElementById("comment-counter");
    counter.textContent = `${length} / ${MAX_BODY_LENGTH}`;
    counter.classList.toggle("is-over", length > MAX_BODY_LENGTH);
  } else if (target === postBody) {
    updateComposeState();
  }
});

postImageInput.addEventListener("change", () => {
  const files = [...postImageInput.files];
  postImageInput.value = ""; // 同じファイルを続けて選べるようにする
  addImages(files);
});

// Esc キーで閉じるときも、編集中の変更があれば確認する
postModal.addEventListener("cancel", (e) => {
  e.preventDefault();
  closeComposeModal();
});

postModal.addEventListener("close", () => {
  compose = null;
});

deleteModal.addEventListener("close", () => {
  pendingDeletePostId = null;
});

window.addEventListener("hashchange", render);

// ---------- 起動 ----------

if (!localStorage.getItem(STORAGE_KEY)) saveState();
render();
