package com.example.raisetime.validation;

// 本文に「見える文字」が1文字でもあるかを調べる（投稿 F-04・コメント F-05 の「空白だけは不可」）。
// String.isBlank() は普通の空白や改行しか空白と見なさず、次のような文字だけの本文を通してしまうため、これも空白として扱う
// - 改行しないスペース（U+00A0 など。Character.isSpaceChar）
// - ゼロ幅スペースなどの「書式文字」（U+200B・U+FEFF など。Unicode の Cf）
// 画面側（frontend/src/utils/text.js の hasVisibleText）も同じ判定にしている
public final class VisibleText {

    private VisibleText() {
    }

    public static boolean hasVisibleCharacter(String text) {
        return text != null && text.codePoints().anyMatch(VisibleText::isVisible);
    }

    private static boolean isVisible(int codePoint) {
        return !Character.isWhitespace(codePoint)
                && !Character.isSpaceChar(codePoint)
                && Character.getType(codePoint) != Character.FORMAT;
    }
}
