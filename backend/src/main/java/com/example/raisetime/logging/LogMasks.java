package com.example.raisetime.logging;

// ログに出す前に、個人情報の一部を隠す（docs/ログ・監視設計.md 第2部 3.3）
public final class LogMasks {

    private LogMasks() {
    }

    private static final String HIDDEN = "***";
    // メールアドレスの長さの上限（新規登録と同じ）。これより長いものはメールアドレスとして扱わない
    private static final int MAX_EMAIL_LENGTH = 255;

    // メールアドレスは、@ より前を1文字だけ残して隠す（taro@example.com → t***@example.com）。
    // ドメインは残す（どのドメインへの攻撃が多いかを見るため）。メールアドレスの形でなければ全部隠す。
    // 利用者が送った値がそのままログに入るので、次のものも全部隠す（ログインの入力は形式を確かめていない）
    // - 改行などの制御文字・空白を含む：改行で偽のログの行を作れてしまう（ログインジェクション）
    // - 長すぎる：ログを埋められてしまう
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1 || email.length() > MAX_EMAIL_LENGTH
                || email.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)
                        || Character.isSpaceChar(c))) {
            return HIDDEN;
        }
        return email.charAt(0) + HIDDEN + email.substring(at);
    }
}
