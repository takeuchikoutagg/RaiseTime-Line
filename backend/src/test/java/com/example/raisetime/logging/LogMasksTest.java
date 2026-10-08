package com.example.raisetime.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMasksTest {

    @Test
    void email_keepsFirstCharacterAndDomain() {
        assertThat(LogMasks.email("taro@example.com")).isEqualTo("t***@example.com");
        assertThat(LogMasks.email("a@example.com")).isEqualTo("a***@example.com");
    }

    // メールアドレスの形でないもの（打ち間違いでパスワードを入れた、など）は、何も残さない
    @Test
    void email_notAnEmailAddress_isFullyMasked() {
        assertThat(LogMasks.email("password123")).isEqualTo("***");
        assertThat(LogMasks.email("@example.com")).isEqualTo("***");
        assertThat(LogMasks.email("taro@")).isEqualTo("***");
        assertThat(LogMasks.email("")).isEqualTo("***");
    }

    @Test
    void email_null_staysNull() {
        assertThat(LogMasks.email(null)).isNull();
    }

    // 利用者が送った値がそのままログに入るので、改行などで偽のログの行を作れないようにする
    // （docs/テスト設計書.md 不具合 B-4。ログインの入力は形式を確かめていない）
    @Test
    void email_withControlCharactersOrSpaces_isFullyMasked() {
        for (String value : new String[] {
            "a@evil.example\n2026-10-08 INFO 偽のログ", "a@evil.example\r\nfake", "a\t@example.com",
            "a@exa mple.com", "a@example.com\u0000", "a@example.com\u2028next", "a\u3000@example.com",
            // 改行しないスペース（isWhitespace では空白にならない）
            "a@exa\u00A0mple.com"}) {
            assertThat(LogMasks.email(value)).as(value).isEqualTo("***");
        }
    }

    // 境界値：255文字までは一部を残し、256文字以上は全部隠す（ログを長い値で埋められないように）
    @Test
    void email_longerThan255_isFullyMasked() {
        String domain = "@example.com";
        String max = "a".repeat(255 - domain.length()) + domain;
        assertThat(max).hasSize(255);

        assertThat(LogMasks.email(max)).isEqualTo("a***@example.com");
        assertThat(LogMasks.email("a" + max)).isEqualTo("***");
    }
}
