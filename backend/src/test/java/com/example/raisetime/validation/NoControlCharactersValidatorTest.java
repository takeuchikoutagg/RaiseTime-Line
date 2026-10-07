package com.example.raisetime.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoControlCharactersValidatorTest {

    private final NoControlCharactersValidator validator = new NoControlCharactersValidator();

    @Test
    void singleLineText_rejectsAnyControlCharacter_includingLineBreakAndTab() {
        assertThat(validator.isValid("山田太郎😀", null)).isTrue();
        for (String value : new String[] {"1行目\n2行目", "タブ\tあり", "復帰\rあり", "NUL\u0000あり", "ベル\u0007", "DEL\u007F"}) {
            assertThat(validator.isValid(value, null)).as(value).isFalse();
        }
    }

    // 未入力（null）は、@NotBlank などに任せる
    @Test
    void null_isLeftToOtherChecks() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    // 複数行の文章（本文・コメント・自己紹介）：改行・タブは使える。NUL などは不可（docs/テスト設計書.md B-1）
    @Test
    void multiLineText_allowsLineBreakAndTab_butRejectsOtherControlCharacters() {
        for (String value : new String[] {"普通の文😀", "1行目\n2行目", "1行目\r\n2行目", "タブ\tあり", ""}) {
            assertThat(NoControlCharactersValidator.containsControlCharacterExceptLineBreakAndTab(value)).as(value).isFalse();
        }
        for (String value : new String[] {"a\u0000b", "\u0000", "ベル\u0007", "ESC\u001B[31m", "DEL\u007F", "C1\u0085"}) {
            assertThat(NoControlCharactersValidator.containsControlCharacterExceptLineBreakAndTab(value)).as(value).isTrue();
        }
    }
}
