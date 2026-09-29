package com.example.raisetime.security;

import com.example.raisetime.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 権限がないとき・CSRF トークンが無いか正しくないときに 403 を JSON で返す
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter writer;

    public JsonAccessDeniedHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String message = accessDeniedException instanceof CsrfException
                ? "CSRF トークンが無効です。画面を再読み込みしてください"
                : "この操作を行う権限がありません";
        writer.write(response, HttpServletResponse.SC_FORBIDDEN, ErrorResponse.of("FORBIDDEN", message));
    }
}
