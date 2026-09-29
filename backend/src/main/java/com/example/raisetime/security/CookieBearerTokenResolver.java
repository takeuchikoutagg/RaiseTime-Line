package com.example.raisetime.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

import java.util.Set;

// アクセストークンを Authorization ヘッダーではなく access_token Cookie から読む
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    // ログインしなくても使う API では、アクセストークンを読まない。
    // 期限切れのトークンの Cookie が残っていても、401 にならずにログイン・トークンの更新ができるようにするため
    private static final Set<String> IGNORED_PATHS = Set.of(
            "/api/auth/csrf", "/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Override
    public String resolve(HttpServletRequest request) {
        if (IGNORED_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.ACCESS_TOKEN.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
