package com.example.raisetime.exception;

// 送られたファイルが大きすぎるときの例外。413 になる
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
