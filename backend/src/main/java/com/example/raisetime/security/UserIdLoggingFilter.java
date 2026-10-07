package com.example.raisetime.security;

import com.example.raisetime.logging.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// JWT の確認が済んだあとに、ログイン中のユーザーの id をログの項目（user_id）に入れる。
// 以降のログと、RequestLoggingFilter が出すアクセスログに付く。消すのは RequestLoggingFilter。
// Bean にするとサーブレットのフィルターとしても登録されてしまうため、SecurityConfig で new して入れる
public class UserIdLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            LogContext.setUserId(user.getId());
        }
        chain.doFilter(request, response);
    }
}
