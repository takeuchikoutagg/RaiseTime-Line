package com.example.raisetime.dto;

// GET /api/users/{username}（docs/機能定義書/F-08_ユーザーページ.md）
public record UserProfileResponse(
        String username,
        String name,
        long followingCount,
        long followerCount,
        // ログイン中のユーザーがこの人をフォロー中か。フォローボタンの表示（「フォローする」／「フォロー中」）に使う
        boolean followedByMe,
        // ログイン中のユーザー自身のページか。true ならフォローボタンを出さない
        boolean isMe
) {
}
