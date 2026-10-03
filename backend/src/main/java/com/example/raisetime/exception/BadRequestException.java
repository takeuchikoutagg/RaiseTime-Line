package com.example.raisetime.exception;

// 入力項目の形式ではなく、操作そのものが許されないときの例外（自分自身をフォローしようとした、など）。400 になる
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
