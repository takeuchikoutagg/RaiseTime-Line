package com.example.raisetime.security;

import com.example.raisetime.logging.LogContext;
import com.example.raisetime.logging.LogEvents;
import com.example.raisetime.service.RefreshTokenService;
import com.example.raisetime.support.LogCapture;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Spring Security まわりの小さな部品の、ほかのテストでは通らない分かれ道を確かめる（docs/テスト設計書.md T-29）
class SecurityPartsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        LogContext.clear();
    }

    // ---------- JsonAccessDeniedHandler ----------

    private final JsonAccessDeniedHandler accessDeniedHandler =
            new JsonAccessDeniedHandler(new ErrorResponseWriter(new ObjectMapper()));

    // 今の設定では、CSRF 以外の理由で拒否されることは無い。将来、権限の設定を足したときに通る
    @Test
    void accessDenied_forOtherReasonThanCsrf_returns403_andLogsReasonForbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture logs = LogCapture.of(JsonAccessDeniedHandler.class)) {
            accessDeniedHandler.handle(new MockHttpServletRequest("GET", "/api/admin"), response,
                    new AccessDeniedException("denied"));

            assertThat(LogCapture.fields(logs.single(LogEvents.ACCESS_DENIED))).containsEntry("reason", "forbidden");
        }
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("この操作を行う権限がありません");
    }

    @Test
    void accessDenied_byCsrf_returns403_withReloadMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest("POST", "/api/posts"), response,
                new InvalidCsrfTokenException(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected"), "actual"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF トークンが無効です");
    }

    // ---------- UserIdLoggingFilter ----------

    private String userIdSeenInChain() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(LogContext.USER_ID));
        new UserIdLoggingFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        return seen.get();
    }

    @Test
    void userIdLoggingFilter_putsUserIdOnlyWhenLoggedIn() throws Exception {
        // 認証の情報が無い
        assertThat(userIdSeenInChain()).isNull();

        // ログインしていない人（匿名）
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(userIdSeenInChain()).isNull();

        // ログイン中
        AuthUser user = new AuthUser(42L, "taro_dev", "山田太郎", null);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        assertThat(userIdSeenInChain()).isEqualTo("42");
    }

    // ---------- LogContext ----------

    @Test
    void logContext_ignoresNullUserId() {
        LogContext.setUserId(null);
        assertThat(LogContext.userId()).isNull();

        LogContext.setUserId(7L);
        assertThat(LogContext.userId()).isEqualTo("7");
    }

    // ---------- AuthTokenIssuer ----------

    // プロフィール編集のあと：アクセストークンだけを作り直す（リフレッシュトークン・CSRF トークンは変えない）
    @Test
    void reissueAccessToken_writesOnlyTheAccessTokenCookie() {
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        AuthCookies authCookies = mock(AuthCookies.class);
        CsrfTokenRepository csrfTokenRepository = mock(CsrfTokenRepository.class);
        AuthTokenIssuer issuer =
                new AuthTokenIssuer(accessTokenService, refreshTokenService, authCookies, csrfTokenRepository);
        AuthUser user = new AuthUser(1L, "taro_new", "山田太郎", null);
        when(accessTokenService.issue(user)).thenReturn("new-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        issuer.reissueAccessToken(user, response);

        verify(authCookies).writeAccessToken(response, "new-access-token");
        verify(authCookies, never()).writeRefreshToken(any(), any());
        verify(refreshTokenService, never()).issue(any());
        verify(csrfTokenRepository, never()).saveToken(any(), any(), any());
    }
}
