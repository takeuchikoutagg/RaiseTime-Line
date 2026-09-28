package com.example.raisetime.dto;

import java.util.List;

// エラーレスポンスの共通形式（docs/機能定義書/00_共通仕様.md 3章）
public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> errors
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }

    public static ErrorResponse of(String code, String message, List<FieldErrorResponse> errors) {
        return new ErrorResponse(code, message, List.copyOf(errors));
    }

    public record FieldErrorResponse(String field, String message) {
    }
}
