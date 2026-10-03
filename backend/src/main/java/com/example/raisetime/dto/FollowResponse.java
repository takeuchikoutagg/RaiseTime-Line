package com.example.raisetime.dto;

// PUT / DELETE /api/users/{username}/follow（docs/機能定義書/F-07_フォロー.md）。画面はこの値でボタンとフォロワー数を更新する
public record FollowResponse(boolean followedByMe, long followerCount) {
}
