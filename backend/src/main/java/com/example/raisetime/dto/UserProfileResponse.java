package com.example.raisetime.dto;

// GET /api/users/{username}（docs/機能定義書/F-08_ユーザーページ.md）・PATCH /api/users/me（F-12）
public record UserProfileResponse(
        String username,
        String name,
        // 自己紹介文（F-12）。設定していなければ空文字
        String bio,
        // プロフィール画像の URL（F-12）。設定していなければ null
        String avatarUrl,
        long followingCount,
        long followerCount,
        // ログイン中のユーザーがこの人をフォロー中か。フォローボタンの表示（「フォローする」／「フォロー中」）に使う
        boolean followedByMe,
        // ログイン中のユーザー自身のページか。true ならフォローボタンの代わりに「プロフィールを編集」を出す
        boolean isMe
) {
}
