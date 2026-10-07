package com.example.raisetime.logging;

// ログの event に入れる名前（docs/ログ・監視設計.md 第2部 3.2）。
// 監視（Datadog の Monitors など）はこの名前で絞るので、メッセージの文言を変えても監視は壊れない。
// 名前を変える・足すときは、設計書の一覧も直す
public final class LogEvents {

    public static final String HTTP_REQUEST = "http_request";
    public static final String LOGIN_SUCCEEDED = "login_succeeded";
    public static final String LOGIN_FAILED = "login_failed";
    public static final String LOGIN_LOCKED = "login_locked";
    public static final String SIGNUP_SUCCEEDED = "signup_succeeded";
    public static final String REFRESH_TOKEN_REUSED = "refresh_token_reused";
    public static final String REFRESH_TOKENS_PURGED = "refresh_tokens_purged";
    public static final String ACCESS_DENIED = "access_denied";
    public static final String STORAGE_FAILED = "storage_failed";
    public static final String STORAGE_DELETE_FAILED = "storage_delete_failed";
    public static final String UNEXPECTED_ERROR = "unexpected_error";

    private LogEvents() {
    }
}
