// 新規登録の入力チェック。バックエンド（SignupRequest）と同じルール・同じメッセージにする
// （docs/機能定義書/F-01_新規登録.md）

const USERNAME_PATTERN = /^[a-z0-9_]{3,15}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
// BCrypt が扱えるのは72バイトまで
const PASSWORD_MAX_BYTES = 72

// 絵文字も1文字と数える（String.length だと絵文字が2文字になる）
export function countChars(value) {
  return [...value].length
}

function utf8Bytes(value) {
  return new TextEncoder().encode(value).length
}

// { 項目名: メッセージ } を返す。エラーがなければ空のオブジェクト
export function validateSignup({ name, username, email, password, passwordConfirm }) {
  const errors = {}
  const nameLength = countChars(name.trim())
  if (nameLength < 1 || countChars(name) > 50) {
    errors.name = '名前は1〜50文字で入力してください'
  }
  if (!USERNAME_PATTERN.test(username)) {
    errors.username = '@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください'
  }
  const trimmedEmail = email.trim()
  if (!EMAIL_PATTERN.test(trimmedEmail) || trimmedEmail.length > 255) {
    errors.email = 'メールアドレスの形式で入力してください'
  }
  const passwordLength = countChars(password)
  if (passwordLength < 8 || passwordLength > 64) {
    errors.password = 'パスワードは8〜64文字で入力してください'
  } else if (utf8Bytes(password) > PASSWORD_MAX_BYTES) {
    errors.password = 'パスワードが長すぎます。全角文字を減らしてください'
  }
  // 確認用パスワードの一致は画面だけで確認し、API には送らない
  if (passwordConfirm !== password) {
    errors.passwordConfirm = 'パスワード（確認）がパスワードと一致しません'
  }
  return errors
}
