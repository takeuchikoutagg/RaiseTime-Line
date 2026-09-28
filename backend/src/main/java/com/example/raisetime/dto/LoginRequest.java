package com.example.raisetime.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/auth/login（docs/機能定義書/F-02_ログイン・ログアウト.md）
// 形式や文字数の細かいチェックはしない。間違っていればログイン失敗になるだけのため
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください") String email,
        @NotBlank(message = "パスワードを入力してください") String password
) {
}
