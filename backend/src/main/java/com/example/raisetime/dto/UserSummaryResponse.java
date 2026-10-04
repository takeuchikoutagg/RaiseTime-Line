package com.example.raisetime.dto;

// ユーザーの共通レスポンス（docs/機能定義書/00_共通仕様.md 6章の UserSummary）。検索結果（F-09）などで使う
public record UserSummaryResponse(
        String username,
        String name,
        // プロフィール画像の URL。設定していなければ null
        String avatarUrl,
        // ログイン中のユーザーがこの人をフォロー中か
        boolean followedByMe,
        // ログイン中のユーザー自身か。true ならフォローボタンの代わりに「あなた」と表示する
        boolean isMe
) {
}
