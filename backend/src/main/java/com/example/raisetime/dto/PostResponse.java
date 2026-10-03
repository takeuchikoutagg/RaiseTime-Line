package com.example.raisetime.dto;

import java.time.Instant;
import java.util.List;

// 投稿1件のレスポンス（docs/機能定義書/00_共通仕様.md 5章の PostResponse）。
// インプレッション数・リツイート数などは持たない
public record PostResponse(
        Long id,
        String body,
        Instant createdAt,
        // 編集していなければ null
        Instant editedAt,
        Author author,
        List<Image> images,
        long likeCount,
        long commentCount,
        // ログイン中のユーザーがいいね済みか。いいねボタンの見た目（♡ / ♥）の切り替えに使う
        boolean likedByMe,
        // ログイン中のユーザーの投稿か。画面はこれを見て「…」メニュー（削除など）を出すか決める。
        // 表示の切り替えのためだけのもので、削除の API では必ずサーバー側で投稿者を確認する
        boolean isMine
) {

    // avatarUrl：プロフィール画像の URL（F-12）。設定していなければ null（画面は名前の1文字目の丸を出す）
    public record Author(String username, String name, String avatarUrl) {
    }

    public record Image(Long id, String url, int position) {
    }
}
