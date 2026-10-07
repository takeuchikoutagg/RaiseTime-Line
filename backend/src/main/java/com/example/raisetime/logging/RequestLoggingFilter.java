package com.example.raisetime.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;

// リクエスト1件ごとに、リクエストID を作ってログに付け、終わったらアクセスログを1行出す
// （docs/ログ・監視設計.md 第2部 3.1・3.2）。
// Spring Security のフィルターより外側で動かすので、401・403 で止まったリクエストも記録される
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    // ALB が数十秒ごとに呼ぶので、記録しない（量が多く、成功している間は役に立たない）
    private static final String HEALTH_PATH = "/actuator/health";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        // 利用者が送ってきた値は使わず、必ずサーバーで作る（ログに好きな文字を入れられないように）
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LogContext.setRequestId(requestId);
        // 利用者から「うまくいかなかった」と言われたとき、この値でログを特定できる
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
            logAccess(request, response.getStatus(), startedAt);
        } catch (ServletException | IOException | RuntimeException e) {
            // ここまで例外が来たら、サーバーは 500 を返す
            logAccess(request, HttpStatus.INTERNAL_SERVER_ERROR.value(), startedAt);
            throw e;
        } finally {
            LogContext.clear();
        }
    }

    private static void logAccess(HttpServletRequest request, int status, long startedAt) {
        String path = path(request);
        if (path.startsWith(HEALTH_PATH)) {
            return;
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.atInfo()
                .addKeyValue(LogContext.EVENT, LogEvents.HTTP_REQUEST)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("duration_ms", durationMs)
                .addKeyValue("client_ip", request.getRemoteAddr())
                .log("{} {} {} {}ms", request.getMethod(), path, status, durationMs);
    }

    // /api/posts/{postId} のような「パスの形」を出す（id ごとに別のパスにならず、API ごとに集計できる）。
    // 形が決まらなかったとき（401・404 など）は実際のパスを出す。検索キーワードなどが入るクエリ文字列は出さない
    private static String path(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }
}
