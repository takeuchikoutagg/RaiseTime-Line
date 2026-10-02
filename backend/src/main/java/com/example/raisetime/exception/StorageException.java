package com.example.raisetime.exception;

// 画像の保存先（S3）とのやり取りに失敗したときの例外。500 になる
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
