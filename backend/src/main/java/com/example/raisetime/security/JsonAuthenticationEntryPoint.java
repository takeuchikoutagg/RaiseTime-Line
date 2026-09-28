package com.example.raisetime.security;

import com.example.raisetime.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// ログインしていないときに 401 を JSON で返す（ログイン画面へのリダイレクトはしない。画面の切り替えは React が行う）
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public JsonAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writer.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorResponse.of("UNAUTHORIZED", "ログインしてください"));
    }
}
