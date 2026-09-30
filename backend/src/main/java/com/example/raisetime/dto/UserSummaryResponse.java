package com.example.raisetime.dto;

import com.example.raisetime.entity.UserSummary;

// 一覧に出すユーザー（docs/機能定義書/00_共通仕様.md 6章の UserSummary）
public record UserSummaryResponse(
        String username,
        String name,
        boolean followedByMe,
        boolean isMe
) {

    public static UserSummaryResponse of(UserSummary user, Long viewerId) {
        return new UserSummaryResponse(user.getUsername(), user.getName(), user.isFollowedByMe(),
                user.getId().equals(viewerId));
    }
}
