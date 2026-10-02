package com.example.raisetime.exception;

// ログインはしているが、その操作をしてよい人ではないときの例外（他人の投稿を削除しようとした、など）。403 になる
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
