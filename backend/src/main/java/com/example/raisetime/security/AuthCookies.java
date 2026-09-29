package com.example.raisetime.security;

import com.example.raisetime.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

// トークンを入れる Cookie。どちらも JavaScript から読めない（HttpOnly）
@Component
public class AuthCookies {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    // アクセストークンは API 全体に送る
    private static final String ACCESS_TOKEN_PATH = "/api";
    // リフレッシュトークンは認証の API（/api/auth/refresh・logout など）にだけ送り、送られる機会を減らす
    private static final String REFRESH_TOKEN_PATH = "/api/auth";

    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public void writeAccessToken(HttpServletResponse response, String token) {
        add(response, ACCESS_TOKEN, token, ACCESS_TOKEN_PATH, "Lax", properties.accessTokenTtl());
    }

    // 画面は同じサイトからしか使わないため、リフレッシュトークンは Strict にして別サイトからは一切送らせない
    public void writeRefreshToken(HttpServletResponse response, String token) {
        add(response, REFRESH_TOKEN, token, REFRESH_TOKEN_PATH, "Strict", properties.refreshTokenTtl());
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS_TOKEN, "", ACCESS_TOKEN_PATH, "Lax", Duration.ZERO);
        add(response, REFRESH_TOKEN, "", REFRESH_TOKEN_PATH, "Strict", Duration.ZERO);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void add(HttpServletResponse response, String name, String value, String path, String sameSite,
                     Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
