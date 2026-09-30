package com.example.raisetime.exception;

// ユーザーや投稿が存在しないときの例外。404 になる
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
