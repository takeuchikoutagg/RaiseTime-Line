package com.example.raisetime.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisibleTextTest {

    @Test
    void normalTextAndEmoji_areVisible() {
        assertThat(VisibleText.hasVisibleCharacter("こんにちは")).isTrue();
        assertThat(VisibleText.hasVisibleCharacter("  a  ")).isTrue();
        assertThat(VisibleText.hasVisibleCharacter("😀")).isTrue();
        // ゼロ幅接合子でつないだ絵文字（家族の絵文字など）も、見える文字がある
        assertThat(VisibleText.hasVisibleCharacter("👨‍👩‍👧")).isTrue();
    }

    @Test
    void emptyOrOnlyWhitespace_isNotVisible() {
        assertThat(VisibleText.hasVisibleCharacter(null)).isFalse();
        assertThat(VisibleText.hasVisibleCharacter("")).isFalse();
        assertThat(VisibleText.hasVisibleCharacter(" \t\r\n")).isFalse();
        // 全角スペース
        assertThat(VisibleText.hasVisibleCharacter("　　")).isFalse();
    }

    @Test
    void onlyInvisibleCharacters_isNotVisible() {
        // 改行しないスペース（isBlank では空白と見なされない）
        assertThat(VisibleText.hasVisibleCharacter(" ")).isFalse();
        assertThat(VisibleText.hasVisibleCharacter("  ")).isFalse();
        // ゼロ幅スペース・ゼロ幅接合子・BOM などの書式文字
        assertThat(VisibleText.hasVisibleCharacter("​")).isFalse();
        assertThat(VisibleText.hasVisibleCharacter("​‌‍⁠﻿")).isFalse();
        assertThat(VisibleText.hasVisibleCharacter(" ​  ")).isFalse();
    }
}
