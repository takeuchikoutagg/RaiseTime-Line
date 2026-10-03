import { request } from './client.js'

// ユーザーページ・フォローの API（docs/機能定義書/F-08_ユーザーページ.md・F-07_フォロー.md）

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
