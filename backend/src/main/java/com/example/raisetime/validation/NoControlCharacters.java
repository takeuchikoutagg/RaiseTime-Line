package com.example.raisetime.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 改行・タブ・ベル音などの制御文字（Unicode の Cc）を含まないかのチェック。
// 名前のように1行で表示する項目に使う（画面の入力欄からは入らないが、API を直接呼ばれると入れられるため）
@Documented
@Constraint(validatedBy = NoControlCharactersValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoControlCharacters {

    String message() default "使えない文字が含まれています";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
