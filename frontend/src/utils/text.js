// 絵文字も1文字と数える（String.length だと絵文字が2文字になる）。バックエンドの codePointCount と同じ数え方
export function countChars(value) {
  return [...value].length
}

// 「見える文字」が1文字でもあるか（投稿・コメントの「空白だけは不可」）。
// 空白（\s。改行しないスペースや全角スペースも含む）と、ゼロ幅スペースなどの書式文字（\p{Cf}）だけなら false。
// バックエンドの VisibleText と同じ判定
export function hasVisibleText(value) {
  return /[^\s\p{Cf}]/u.test(value)
}
