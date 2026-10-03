import { isValidBody, MAX_BODY_LENGTH } from './post.js'

// コメントの入力チェック。バックエンド（CommentService）と同じルール・同じメッセージにする（docs/機能定義書/F-05_コメント.md）
// 文字数のルールは投稿の本文と同じ（1〜280文字、空白だけは不可、絵文字も1文字）

export const MAX_COMMENT_LENGTH = MAX_BODY_LENGTH
export const COMMENT_MESSAGE = 'コメントは1〜280文字で入力してください'

export function isValidComment(body) {
  return isValidBody(body)
}
