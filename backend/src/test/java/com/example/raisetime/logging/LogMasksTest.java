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
}
