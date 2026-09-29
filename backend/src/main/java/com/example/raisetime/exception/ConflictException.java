package com.example.raisetime.exception;

import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;

import java.util.List;

// すでに使われている値（@ユーザーID・メールアドレスなど）で登録しようとしたときの例外。409 になる
public class ConflictException extends RuntimeException {

    private final List<FieldErrorResponse> errors;

    public ConflictException(List<FieldErrorResponse> errors) {
        super("conflict: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<FieldErrorResponse> getErrors() {
        return errors;
    }
}
