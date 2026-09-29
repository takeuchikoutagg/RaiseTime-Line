package com.example.raisetime.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CodePointLengthValidator implements ConstraintValidator<CodePointLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(CodePointLength annotation) {
        this.min = annotation.min();
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null のチェックは @NotNull / @NotBlank に任せる
        if (value == null) {
            return true;
        }
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }
}
