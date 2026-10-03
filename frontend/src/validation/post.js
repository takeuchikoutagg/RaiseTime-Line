import { countChars, hasVisibleText } from '../utils/text.js'

// 投稿の入力チェック。バックエンド（PostService）と同じルール・同じメッセージにする（docs/機能定義書/F-04_投稿作成.md）

export const MAX_BODY_LENGTH = 280
export const MAX_IMAGES = 4
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

export const BODY_MESSAGE = '本文は1〜280文字で入力してください'
export const TOO_MANY_IMAGES = '画像は4枚まで添付できます'
export const IMAGE_TYPE_MESSAGE = 'JPEG・PNG・GIF・WebP の画像を選んでください'
export const IMAGE_TOO_LARGE = '画像は1枚5MBまでです'

// 空白・見えない文字（ゼロ幅スペースなど）だけは不可。絵文字も1文字と数える
export function isValidBody(body) {
  return hasVisibleText(body) && countChars(body) <= MAX_BODY_LENGTH
}

// 選んだファイルを、すでに選んでいる枚数と合わせてチェックする。
// 追加できるファイルと、最初に見つかったエラーのメッセージを返す（画像かどうかの最終的な判定はサーバーが中身で行う）
export function checkImages(files, currentCount) {
  const accepted = []
  let error = ''
  for (const file of files) {
    if (currentCount + accepted.length >= MAX_IMAGES) {
      error = TOO_MANY_IMAGES
      break
    }
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      error ||= IMAGE_TYPE_MESSAGE
      continue
    }
    if (file.size > MAX_IMAGE_BYTES) {
      error ||= IMAGE_TOO_LARGE
      continue
    }
    accepted.push(file)
  }
  return { accepted, error }
}
