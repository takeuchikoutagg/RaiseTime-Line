package com.example.raisetime.service;

import java.util.Locale;

// メールアドレスは前後の空白を除き、小文字にそろえてから保存・照合する（Taro@Example.com と taro@example.com を同じにする）
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
