package com.example.raisetime.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// UTF-8 にしたときのバイト数の上限チェック。
// BCrypt は72バイトまでしか扱えないため、全角文字の多いパスワードは64文字以内でも弾く
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "長すぎます";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
