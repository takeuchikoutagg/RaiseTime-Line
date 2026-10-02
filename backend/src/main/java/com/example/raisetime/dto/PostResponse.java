package com.example.raisetime.dto;

import java.time.Instant;
import java.util.List;

// 投稿1件のレスポンス（docs/機能定義書/00_共通仕様.md 5章の PostResponse）。
// いいね数・コメント数・likedByMe は、いいね・コメントの機能を作るときに足す。インプレッション数などは持たない
public record PostResponse(
        Long id,
        String body,
        Instant createdAt,
        // 編集していなければ null
        Instant editedAt,
        Author author,
        List<Image> images,
        // ログイン中のユーザーの投稿か。画面はこれを見て「…」メニュー（削除など）を出すか決める。
        // 表示の切り替えのためだけのもので、削除の API では必ずサーバー側で投稿者を確認する
        boolean isMine
) {

    public record Author(String username, String name) {
    }

    public record Image(Long id, String url, int position) {
    }
}
