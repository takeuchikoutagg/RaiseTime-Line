package com.example.raisetime.security;

import com.example.raisetime.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

// ログイン・新規登録・トークン更新・プロフィール編集のあとに、アクセストークンとリフレッシュトークンを Cookie で渡す
@Component
public class AuthTokenIssuer {

    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookies authCookies;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthTokenIssuer(AccessTokenService accessTokenService, RefreshTokenService refreshTokenService,
                           AuthCookies authCookies, CsrfTokenRepository csrfTokenRepository) {
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.authCookies = authCookies;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    // ログイン・新規登録の直後：新しい系列のリフレッシュトークンを作る
    public void issueForLogin(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        authCookies.writeAccessToken(response, accessTokenService.issue(user));
        authCookies.writeRefreshToken(response, refreshTokenService.issue(user.getId()));
        // ログインの前後で CSRF トークンを変える（新しいトークンの Cookie を返す）
        CsrfToken newCsrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(newCsrfToken, request, response);
    }

    // プロフィール編集（F-12）のあと：アクセストークン（JWT）には @ユーザーID と名前が入っているので、新しい値で作り直す
    public void reissueAccessToken(AuthUser user, HttpServletResponse response) {
        authCookies.writeAccessToken(response, accessTokenService.issue(user));
    }

    // トークン更新：交換済みの新しいリフレッシュトークンと、新しいアクセストークンを渡す
    public void issueForRefresh(AuthUser user, String newRefreshToken, HttpServletResponse response) {
        authCookies.writeAccessToken(response, accessTokenService.issue(user));
        authCookies.writeRefreshToken(response, newRefreshToken);
    }
}
