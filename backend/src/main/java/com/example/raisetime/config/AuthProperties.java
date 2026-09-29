package com.example.raisetime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

// application.properties の app.auth.*（JWT とトークン Cookie の設定）
@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure
) {

    // HS256 の鍵は256ビット（32バイト）以上が必要
    private static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("app.auth.jwt-secret は32バイト以上にしてください");
        }
    }
}
