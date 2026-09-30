package com.example.raisetime.exception;

// 入力の形式は正しいが、受け付けられない操作のときの例外（自分自身をフォローする、など）。400 になる
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
