package com.example.raisetime.dto;

import com.example.raisetime.security.AuthUser;

// signup / login / refresh / me のレスポンス（ログイン中のユーザー）。
// avatarUrl はプロフィール画像の URL（F-12）。設定していなければ null
public record UserResponse(String username, String name, String avatarUrl) {

    // 登録した直後など、プロフィール画像がまだ無いとき
    public static UserResponse from(AuthUser user) {
        return new UserResponse(user.getUsername(), user.getName(), null);
    }
}
