package com.example.raisetime.exception;

import java.time.Duration;

// 短い時間に何度も失敗した（ログインの総当たり対策）。429 になり、retryAfter 後にまた試せる
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
