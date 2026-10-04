package com.example.raisetime.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineBreaksTest {

    @Test
    void crlfAndCr_becomeLf() {
        assertThat(LineBreaks.normalize("1行目\r\n2行目\r3行目\n4行目")).isEqualTo("1行目\n2行目\n3行目\n4行目");
        assertThat(LineBreaks.normalize("改行なし")).isEqualTo("改行なし");
        assertThat(LineBreaks.normalize(null)).isNull();
    }
}
