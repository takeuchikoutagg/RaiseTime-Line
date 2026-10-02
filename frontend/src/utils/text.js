// 絵文字も1文字と数える（String.length だと絵文字が2文字になる）。バックエンドの codePointCount と同じ数え方
export function countChars(value) {
  return [...value].length
}
