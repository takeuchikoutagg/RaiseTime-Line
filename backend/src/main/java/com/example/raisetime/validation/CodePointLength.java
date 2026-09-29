package com.example.raisetime.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 文字数を「見た目の1文字」（コードポイント）で数えるチェック。
// @Size は String.length() を使うため、絵文字が2文字と数えられてしまう
@Documented
@Constraint(validatedBy = CodePointLengthValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodePointLength {

    int min() default 0;

    int max() default Integer.MAX_VALUE;

    String message() default "文字数が正しくありません";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
