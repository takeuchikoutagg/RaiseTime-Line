package com.example.raisetime.exception;

// 投稿などが存在しないときの例外。404 になる
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
