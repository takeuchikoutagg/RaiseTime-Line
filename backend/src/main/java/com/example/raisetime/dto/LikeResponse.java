package com.example.raisetime.dto;

// PUT / DELETE /api/posts/{postId}/like（docs/機能定義書/F-06_いいね.md）。
// 画面は先に見た目を切り替え（楽観的更新）、このサーバーの値で表示を合わせ直す
public record LikeResponse(long likeCount, boolean likedByMe) {
}
