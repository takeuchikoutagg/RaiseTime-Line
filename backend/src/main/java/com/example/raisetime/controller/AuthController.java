package com.example.raisetime.controller;

import com.example.raisetime.dto.LoginRequest;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.dto.UserResponse;
import com.example.raisetime.exception.InvalidRefreshTokenException;
import com.example.raisetime.security.AuthCookies;
import com.example.raisetime.security.AuthTokenIssuer;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.security.LoginAttemptService;
import com.example.raisetime.service.AuthService;
import com.example.raisetime.service.EmailNormalizer;
import com.example.raisetime.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// F-01 新規登録 / F-02 ログイン・ログアウト。
// ログインに成功すると、アクセストークン（JWT、15分）とリフレッシュトークン（7日）を HttpOnly Cookie で返す
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final AuthTokenIssuer authTokenIssuer;
    private final AuthCookies authCookies;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
                          RefreshTokenService refreshTokenService, AuthTokenIssuer authTokenIssuer,
                          AuthCookies authCookies, LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.authTokenIssuer = authTokenIssuer;
        this.authCookies = authCookies;
        this.loginAttemptService = loginAttemptService;
    }

    // CSRF トークンの Cookie（XSRF-TOKEN）を発行する。画面を開いたときに最初に呼ぶ
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
        // トークンを読み出すと、Cookie がレスポンスに書き込まれる
        csrfToken.getToken();
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthUser user = authService.signup(request);
        // 登録したらそのままログインした状態にする
        authTokenIssuer.issueForLogin(user, httpRequest, httpResponse);
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = EmailNormalizer.normalize(request.email());
        // ログインの失敗が続いていたら、パスワードを確かめる前に 429 で止める（総当たり対策）
        String ip = httpRequest.getRemoteAddr();
        loginAttemptService.checkAllowed(email, ip);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException e) {
            // 失敗を数えてから投げ直す（GlobalExceptionHandler で 401 になる）
            loginAttemptService.recordFailure(email, ip);
            throw e;
        }
        loginAttemptService.recordSuccess(email);
        AuthUser user = (AuthUser) authentication.getPrincipal();
        authTokenIssuer.issueForLogin(user, httpRequest, httpResponse);
        return authService.describe(user.getId()).orElseGet(() -> UserResponse.from(user));
    }

    // アクセストークンの期限が切れたら、画面から呼んでもらう。
    // リフレッシュトークンを新しいものに交換し、アクセストークンも作り直す
    @PostMapping("/refresh")
    public UserResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            String rawToken = authCookies.readRefreshToken(httpRequest)
                    .orElseThrow(() -> new InvalidRefreshTokenException("no refresh token"));
            RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawToken);
            AuthUser user = authService.findUser(rotation.userId())
                    .orElseThrow(() -> new InvalidRefreshTokenException("user not found"));
            authTokenIssuer.issueForRefresh(user, rotation.newToken(), httpResponse);
            return authService.describe(user.getId()).orElseGet(() -> UserResponse.from(user));
        } catch (InvalidRefreshTokenException e) {
            // 使えないトークンの Cookie は消しておく（GlobalExceptionHandler で 401 になる）
            authCookies.clear(httpResponse);
            throw e;
        }
    }

    // リフレッシュトークンを無効にし、両方の Cookie を消す。
    // アクセストークン（JWT）は取り消せないが、Cookie を消すのでこのブラウザからは使われない。有効期限は最長15分
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authCookies.readRefreshToken(httpRequest).ifPresent(refreshTokenService::revoke);
        authCookies.clear(httpResponse);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        // 名前・プロフィール画像は DB の最新の値を返す。アクセストークンが有効でもユーザーが削除されていたら 401
        return authService.describe(user.getId())
                .orElseThrow(() -> new InvalidRefreshTokenException("user not found"));
    }
}
