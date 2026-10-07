package com.example.raisetime.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.raisetime.support.LogCapture;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("203.0.113.1");
        return request;
    }

    @Test
    void logsOneAccessLogPerRequest_withRequestIdAndFields() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/posts/10");
        request.setQueryString("secret=query");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] requestIdInChain = new String[1];
        FilterChain chain = (req, res) -> {
            // Spring MVC が入れる「パスの形」と、ログイン中のユーザー
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/posts/{postId}");
            LogContext.setUserId(42L);
            requestIdInChain[0] = MDC.get(LogContext.REQUEST_ID);
            ((MockHttpServletResponse) res).setStatus(200);
        };

        try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
            filter.doFilter(request, response, chain);

            ILoggingEvent event = logs.single(LogEvents.HTTP_REQUEST);
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            Map<String, Object> fields = LogCapture.fields(event);
            assertThat(fields).containsEntry("method", "GET")
                    // id ごとに別のパスにならないよう、パスの形で出す
                    .containsEntry("path", "/api/posts/{postId}")
                    .containsEntry("status", 200)
                    .containsEntry("client_ip", "203.0.113.1")
                    .containsKey("duration_ms");
            // リクエストID は、処理の間のログ・アクセスログ・レスポンスのヘッダーで同じ値
            String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
            assertThat(requestId).matches("[0-9a-f]{16}");
            assertThat(requestIdInChain[0]).isEqualTo(requestId);
            assertThat(event.getMDCPropertyMap())
                    .containsEntry(LogContext.REQUEST_ID, requestId)
                    .containsEntry(LogContext.USER_ID, "42");
            // 検索キーワードなどが入るクエリ文字列は出さない
            assertThat(logs.allText()).doesNotContain("secret=query");
        }
        // 次のリクエスト（同じスレッドを使い回す）に残さない
        assertThat(MDC.get(LogContext.REQUEST_ID)).isNull();
        assertThat(MDC.get(LogContext.USER_ID)).isNull();
    }

    @Test
    void requestIdFromClient_isIgnored() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/timeline");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "evil\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).matches("[0-9a-f]{16}");
    }

    @Test
    void withoutPathPattern_logsActualPath() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
            filter.doFilter(request("GET", "/api/posts/10"), response,
                    (req, res) -> ((MockHttpServletResponse) res).setStatus(401));

            assertThat(LogCapture.fields(logs.single(LogEvents.HTTP_REQUEST)))
                    .containsEntry("path", "/api/posts/10")
                    .containsEntry("status", 401);
        }
    }

    @Test
    void healthCheck_isNotLogged_butStillGetsRequestId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
            filter.doFilter(request("GET", "/actuator/health"), response, (req, res) -> { });

            assertThat(logs.events()).isEmpty();
        }
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void exceptionFromChain_isLoggedAs500_andRethrown_andMdcIsCleared() {
        FilterChain failing = (req, res) -> {
            throw new ServletException("boom");
        };

        try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
            assertThatThrownBy(() -> filter.doFilter(request("POST", "/api/posts"), new MockHttpServletResponse(), failing))
                    .isInstanceOf(ServletException.class);

            assertThat(LogCapture.fields(logs.single(LogEvents.HTTP_REQUEST))).containsEntry("status", 500);
        }
        assertThat(MDC.get(LogContext.REQUEST_ID)).isNull();
    }
}
