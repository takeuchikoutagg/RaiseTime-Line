package com.example.raisetime.exception;

// 入力が仕様に合わないときの例外（本文の文字数、画像の枚数・形式など）。400 になり、どの項目かを errors[].field で返す
public class InvalidInputException extends RuntimeException {

    private final String field;

    public InvalidInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
