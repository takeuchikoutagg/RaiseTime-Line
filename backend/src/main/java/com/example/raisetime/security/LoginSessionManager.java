package com.example.raisetime.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

// ログイン状態をセッションに保存する（ログイン・新規登録の直後に呼ぶ）。
// formLogin を使わず自分の API でログインするため、Spring Security が自動でやってくれる処理をここで行う
@Component
public class LoginSessionManager {

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public LoginSessionManager(SecurityContextRepository securityContextRepository,
                               CsrfTokenRepository csrfTokenRepository) {
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public void start(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        // セッション固定攻撃対策：ログイン前から使っていたセッションがあれば、ID を新しくする
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // ログインの前後で CSRF トークンを変える（新しいトークンの Cookie を返す）
        CsrfToken newToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(newToken, request, response);
    }
}
