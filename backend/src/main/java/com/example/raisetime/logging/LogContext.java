package com.example.raisetime.logging;

import org.slf4j.MDC;

// すべてのログに自動で付ける項目（MDC）。リクエストごとに RequestLoggingFilter が入れ、終わったら消す。
// 項目名は docs/ログ・監視設計.md 第1部 2.4 にそろえる（ある場所は userId、別の場所は user_id、とならないように）
public final class LogContext {

    public static final String REQUEST_ID = "request_id";
    public static final String USER_ID = "user_id";

    // ログの種類を表す項目の名前。値は LogEvents の決まった名前を使う
    public static final String EVENT = "event";

    private LogContext() {
    }

    public static void setRequestId(String requestId) {
        MDC.put(REQUEST_ID, requestId);
    }

    // ログイン中のユーザーがわかった時点で呼ぶ。以降のログとアクセスログに user_id が付く
    public static void setUserId(Long userId) {
        if (userId != null) {
            MDC.put(USER_ID, String.valueOf(userId));
        }
    }

    public static String userId() {
        return MDC.get(USER_ID);
    }

    public static void clear() {
        MDC.remove(REQUEST_ID);
        MDC.remove(USER_ID);
    }
}
