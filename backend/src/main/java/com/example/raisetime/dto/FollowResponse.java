package com.example.raisetime.dto;

// PUT / DELETE /api/users/{username}/follow（docs/機能定義書/F-07_フォロー.md）
public record FollowResponse(boolean followedByMe, long followerCount) {
}
