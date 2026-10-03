import { countChars, hasVisibleText } from '../utils/text.js'

// プロフィール編集の入力チェック。バックエンド（UserService）と同じルール・同じメッセージにする
// （docs/機能定義書/F-12_プロフィール編集.md）

export const MAX_NAME_LENGTH = 50
export const MAX_BIO_LENGTH = 160
export const NAME_MESSAGE = '名前は1〜50文字で入力してください'
export const USERNAME_MESSAGE = '@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください'
export const BIO_MESSAGE = '自己紹介は160文字以内で入力してください'

const USERNAME_PATTERN = /^[a-z0-9_]{3,15}$/

// 項目ごとのエラーメッセージを返す。エラーが無ければ空のオブジェクト
export function validateProfile({ name, username, bio }) {
  const errors = {}
  const trimmedName = name.trim()
  if (!hasVisibleText(trimmedName) || countChars(trimmedName) > MAX_NAME_LENGTH) {
    errors.name = NAME_MESSAGE
  }
  if (!USERNAME_PATTERN.test(username)) {
    errors.username = USERNAME_MESSAGE
  }
  if (countChars(bio) > MAX_BIO_LENGTH) {
    errors.bio = BIO_MESSAGE
  }
  return errors
}
