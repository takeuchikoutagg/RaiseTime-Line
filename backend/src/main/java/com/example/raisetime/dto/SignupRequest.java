package com.example.raisetime.dto;

import com.example.raisetime.validation.CodePointLength;
import com.example.raisetime.validation.MaxUtf8Bytes;
import com.example.raisetime.validation.NoControlCharacters;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// POST /api/auth/signup（docs/機能定義書/F-01_新規登録.md）
// 1つの項目で複数のチェックに引っかかっても同じメッセージになるよう、項目ごとにメッセージをそろえている
public record SignupRequest(
        @NotBlank(message = NAME_MESSAGE)
        @CodePointLength(max = 50, message = NAME_MESSAGE)
        // 名前は1行で表示するので、改行・タブなどの制御文字は使えない（プロフィール編集 F-12 と同じ）
        @NoControlCharacters(message = NAME_MESSAGE)
        String name,

        @NotNull(message = USERNAME_MESSAGE)
        @Pattern(regexp = "^[a-z0-9_]{3,15}$", message = USERNAME_MESSAGE)
        String username,

        @NotBlank(message = EMAIL_MESSAGE)
        @Email(message = EMAIL_MESSAGE)
        @Size(max = 255, message = EMAIL_MESSAGE)
        String email,

        @NotNull(message = PASSWORD_MESSAGE)
        @CodePointLength(min = 8, max = 64, message = PASSWORD_MESSAGE)
        @MaxUtf8Bytes(value = 72, message = PASSWORD_BYTES_MESSAGE)
        String password
) {

    static final String NAME_MESSAGE = "名前は1〜50文字で入力してください";
    static final String USERNAME_MESSAGE = "@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください";
    static final String EMAIL_MESSAGE = "メールアドレスの形式で入力してください";
    static final String PASSWORD_MESSAGE = "パスワードは8〜64文字で入力してください";
    static final String PASSWORD_BYTES_MESSAGE = "パスワードが長すぎます。全角文字を減らしてください";
}
