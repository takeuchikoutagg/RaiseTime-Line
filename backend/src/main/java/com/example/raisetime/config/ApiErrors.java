package com.example.raisetime.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// API 仕様書（OpenAPI）に、その API が返すことのあるエラーのステータスを書く。例：@ApiErrors({400, 404})。
// 中身はどれも共通のエラーレスポンス（ErrorResponse）。説明は OpenApiConfig.ERROR_DESCRIPTIONS の文言になる。
// 401（ログインしていない）と 403（CSRF トークンが無い）は、すべての API に OpenApiConfig が自動で足すので書かない
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiErrors {

    int[] value();
}
