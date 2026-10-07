package com.example.raisetime.logging;

// ログに出す前に、個人情報の一部を隠す（docs/ログ・監視設計.md 第2部 3.3）
public final class LogMasks {

    private LogMasks() {
    }

    // メールアドレスは、@ より前を1文字だけ残して隠す（taro@example.com → t***@example.com）。
    // ドメインは残す（どのドメインへの攻撃が多いかを見るため）。メールアドレスの形でなければ全部隠す
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
