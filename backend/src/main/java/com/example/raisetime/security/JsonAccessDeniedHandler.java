package com.example.raisetime.security;

import com.example.raisetime.dto.ErrorResponse;
import com.example.raisetime.logging.LogContext;
import com.example.raisetime.logging.LogEvents;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 権限がないとき・CSRF トークンが無いか正しくないときに 403 を JSON で返す
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonAccessDeniedHandler.class);

    private final ErrorResponseWriter writer;

    public JsonAccessDeniedHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        boolean csrf = accessDeniedException instanceof CsrfException;
        String message = csrf
                ? "CSRF トークンが無効です。画面を再読み込みしてください"
                : "この操作を行う権限がありません";
        // CSRF トークンが無いリクエストは、画面を長く開いたままのときにも起きるが、続くなら攻撃の試み
        log.atWarn()
                .addKeyValue(LogContext.EVENT, LogEvents.ACCESS_DENIED)
                .addKeyValue("reason", csrf ? "csrf" : "forbidden")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .log("リクエストを拒否しました（{}）: {} {}", csrf ? "CSRF" : "権限なし", request.getMethod(),
                        request.getRequestURI());
        writer.write(response, HttpServletResponse.SC_FORBIDDEN, ErrorResponse.of("FORBIDDEN", message));
    }
}
