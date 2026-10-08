package com.example.raisetime.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// null は「未入力」として、ほかのチェック（@NotBlank など）に任せる
public class NoControlCharactersValidator implements ConstraintValidator<NoControlCharacters, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !containsControlCharacter(value);
    }

    // 1行で表示するもの（名前・検索キーワード）に使う。改行・タブも不可
    public static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    // 複数行の文章（投稿の本文・コメント・自己紹介）に使う。改行（LF・CR）とタブは使えるが、ほかの制御文字は不可。
    // 特に NUL 文字（U+0000）は PostgreSQL が保存できず、通すと保存のときに想定外のエラー（500）になる
    public static boolean containsControlCharacterExceptLineBreakAndTab(String value) {
        return value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }
}
