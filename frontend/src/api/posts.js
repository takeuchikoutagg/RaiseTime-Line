import { request } from './client.js'

// 投稿の API（docs/機能定義書/F-03_タイムライン.md・F-04_投稿作成.md・F-11_投稿削除.md）

// { items: [PostResponse], nextCursor }。続きは before に nextCursor を渡す
export function fetchTimeline(before) {
  const query = before == null ? '' : `?before=${encodeURIComponent(before)}`
  return request(`/api/timeline${query}`)
}

// 本文と画像（File の配列、0〜4枚）を multipart/form-data で送る。作成した PostResponse を返す
export function createPost({ body, images = [] }) {
  const form = new FormData()
  form.append('body', body)
  for (const image of images) {
    form.append('images', image)
  }
  return request('/api/posts', { method: 'POST', body: form })
}

export function deletePost(postId) {
  return request(`/api/posts/${encodeURIComponent(postId)}`, { method: 'DELETE' })
}
