package com.example.raisetime.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

    @Test
    void stripsSpaces_andLowercases() {
        assertThat(EmailNormalizer.normalize("  Taro@Example.COM ")).isEqualTo("taro@example.com");
    }

    // 送られてこなかったとき（null）は、そのまま返して入力チェック（未入力）に任せる
    @Test
    void null_staysNull() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }
}
