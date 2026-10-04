// 絵文字も1文字と数える（String.length だと絵文字が2文字になる）。バックエンドの codePointCount と同じ数え方
export function countChars(value) {
  return [...value].length
}

// 改行・タブ・ベル音などの制御文字（Unicode の Cc）を含むか。名前のように1行で表示する項目に使う
// （バックエンドの @NoControlCharacters と同じ判定）
export function hasControlCharacter(value) {
  return /\p{Cc}/u.test(value)
}

// 「見える文字」が1文字でもあるか（投稿・コメントの「空白だけは不可」）。
// 空白（\s。改行しないスペースや全角スペースも含む）と、ゼロ幅スペースなどの書式文字（\p{Cf}）だけなら false。
// バックエンドの VisibleText と同じ判定
export function hasVisibleText(value) {
  return /[^\s\p{Cf}]/u.test(value)
}

// 検索キーワードの前後の空白と、先頭の @ を取り除く（「@taro」でも「taro」でも同じ結果。バックエンドと同じ）
export function normalizeSearchQuery(value) {
  return value.trim().replace(/^@/, '').trim()
}
