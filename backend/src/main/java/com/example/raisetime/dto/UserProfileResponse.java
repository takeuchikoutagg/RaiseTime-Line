package com.example.raisetime.dto;

import com.example.raisetime.entity.UserProfile;

// GET /api/users/{username}（docs/機能定義書/F-08_ユーザーページ.md）
public record UserProfileResponse(
        String username,
        String name,
        long followingCount,
        long followerCount,
        boolean followedByMe,
        // 自分のページか。画面はこれを見てフォローボタンを出すか決める
        boolean isMe
) {

    public static UserProfileResponse of(UserProfile profile, Long viewerId) {
        return new UserProfileResponse(profile.getUsername(), profile.getName(), profile.getFollowingCount(),
                profile.getFollowerCount(), profile.isFollowedByMe(), profile.getId().equals(viewerId));
    }
}
