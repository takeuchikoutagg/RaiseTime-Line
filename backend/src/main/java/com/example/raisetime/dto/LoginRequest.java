package com.example.raisetime.dto;

import com.example.raisetime.validation.CodePointLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /api/auth/login（docs/機能定義書/F-02_ログイン・ログアウト.md）
// 形式の細かいチェックはしない（間違っていればログイン失敗になるだけのため）。
// 長さだけは、新規登録できる上限（メールアドレス255文字・パスワード64文字）までにする。
// 上限が無いと、とても長い値を送って、ログや「失敗回数の記録」を埋められてしまう
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        @Size(max = 255, message = LoginRequest.TOO_LONG_MESSAGE)
        String email,
        @NotBlank(message = "パスワードを入力してください")
        @CodePointLength(max = 64, message = LoginRequest.TOO_LONG_MESSAGE)
        String password
) {
    // 登録できない長さなので、必ずログイン失敗になる値。ログイン失敗と同じメッセージにする
    static final String TOO_LONG_MESSAGE = "メールアドレスまたはパスワードが正しくありません";
}
