package com.example.raisetime.support;

import com.example.raisetime.config.JwtConfig;
import com.example.raisetime.config.SecurityConfig;
import com.example.raisetime.exception.GlobalExceptionHandler;
import com.example.raisetime.security.AccessTokenService;
import com.example.raisetime.security.AuthCookies;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.security.AuthUserDetailsService;
import com.example.raisetime.security.AuthUserJwtAuthenticationConverter;
import com.example.raisetime.security.CookieBearerTokenResolver;
import com.example.raisetime.security.ErrorResponseWriter;
import com.example.raisetime.security.JsonAccessDeniedHandler;
import com.example.raisetime.security.JsonAuthenticationEntryPoint;
import jakarta.servlet.http.Cookie;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

// @WebMvcTest で、本物の Spring Security の設定（JWT の検証・CSRF・401/403 の JSON）を使うための設定。
// 使う側では UserMapper を @MockitoBean にする（AuthUserDetailsService が使うため）
@TestConfiguration
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class, AuthUserDetailsService.class,
        AccessTokenService.class, AuthUserJwtAuthenticationConverter.class, CookieBearerTokenResolver.class,
        ErrorResponseWriter.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
public class WebSecurityTestConfig {

    // ログイン中のユーザーとしてリクエストするための access_token Cookie
    public static Cookie accessTokenCookie(AccessTokenService accessTokenService, AuthUser user) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, accessTokenService.issue(user));
    }
}
