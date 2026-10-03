import { request } from './client.js'

// ユーザーページ・フォロー・プロフィール編集の API（docs/機能定義書/F-08_ユーザーページ.md・F-07_フォロー.md・F-12_プロフィール編集.md）

function userPath(username) {
  return `/api/users/${encodeURIComponent(username)}`
}

// { username, name, followingCount, followerCount, followedByMe, isMe }
export function fetchUser(username) {
  return request(userPath(username))
}

// { items: [PostResponse], nextCursor }。そのユーザーの投稿を新しい順に。続きは before に nextCursor を渡す
export function fetchUserPosts(username, before) {
  const query = before == null ? '' : `?before=${encodeURIComponent(before)}`
  return request(`${userPath(username)}/posts${query}`)
}

// フォロー・フォロー解除。どちらも何度送っても結果が同じ（冪等）。{ followedByMe, followerCount } を返す
export function followUser(username) {
  return request(`${userPath(username)}/follow`, { method: 'PUT' })
}

export function unfollowUser(username) {
  return request(`${userPath(username)}/follow`, { method: 'DELETE' })
}

// 自分のプロフィールを編集する（multipart/form-data）。avatar：新しいプロフィール画像（File）、
// removeAvatar：true なら画像を外す。編集後のユーザー情報（fetchUser と同じ形）を返す
export function updateProfile({ name, username, bio, avatar = null, removeAvatar = false }) {
  const form = new FormData()
  form.append('name', name)
  form.append('username', username)
  form.append('bio', bio)
  if (avatar) {
    form.append('avatar', avatar)
  }
  if (removeAvatar) {
    form.append('removeAvatar', 'true')
  }
  return request('/api/users/me', { method: 'PATCH', body: form })
}
