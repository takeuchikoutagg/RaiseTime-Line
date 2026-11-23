package com.example.raisetime.security;

import com.example.raisetime.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

// リクエストの本文の大きさに上限をかける（docs/テスト設計書.md D-7。issue #81）。
// JSON の API に巨大な本文を送られると、一度すべて読み込んでから 400 を返していた（メモリ・処理を圧迫される）。
// 上限を超えたら、本文を読む前に 413 を返す。画像つきの3つの API（multipart）は、Spring の上限（1枚 5MB・全体 21MB）に任せる。
// アクセスログ（RequestLoggingFilter）の内側、Spring Security（CSRF・ログインの確認）より前で動く
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    // JSON の本文はいちばん大きくても数 KB（新規登録・コメントの 280 文字など）なので、十分に余裕がある
    static final int MAX_BODY_BYTES = 64 * 1024;
    static final String MESSAGE = "リクエストが大きすぎます";

    // 画像を受け取る API（multipart のときだけ、この上限を使わない）
    private static final Pattern POST_ID = Pattern.compile("/api/posts/[^/]+");

    private final ErrorResponseWriter errorResponseWriter;

    public RequestBodyLimitFilter(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isImageUpload(request)) {
            chain.doFilter(request, response);
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_BODY_BYTES) {
            // 本文は読まずに断る
            rejectTooLarge(response);
            return;
        }
        if (contentLength >= 0) {
            // 大きさが分かっていて上限以内。Tomcat は Content-Length より多くは読まない
            chain.doFilter(request, response);
            return;
        }
        // Content-Length が無い（chunked）：上限＋1バイトまでだけ読んで確かめ、読んだ本文を後ろに渡す
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            rejectTooLarge(response);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static boolean isImageUpload(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return false;
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return ("POST".equals(method) && "/api/posts".equals(uri))
                || ("PATCH".equals(method) && POST_ID.matcher(uri).matches())
                || ("PATCH".equals(method) && "/api/users/me".equals(uri));
    }

    private void rejectTooLarge(HttpServletResponse response) throws IOException {
        errorResponseWriter.write(response, HttpStatus.CONTENT_TOO_LARGE.value(),
                ErrorResponse.of("PAYLOAD_TOO_LARGE", MESSAGE));
    }

    // 先に読んだ本文を、もう一度読めるようにしたリクエスト
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("非同期の読み込みは使わない");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
