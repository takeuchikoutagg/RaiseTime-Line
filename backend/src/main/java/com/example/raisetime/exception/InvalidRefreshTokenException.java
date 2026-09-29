package com.example.raisetime.exception;

// リフレッシュトークンが無い・期限切れ・無効・使用済みのときの例外。401 になり、もう一度ログインしてもらう
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
