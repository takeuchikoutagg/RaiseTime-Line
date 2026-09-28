package com.example.raisetime.controller;

import com.example.raisetime.dto.LoginRequest;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.dto.UserResponse;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.security.LoginSessionManager;
import com.example.raisetime.service.AuthService;
import com.example.raisetime.service.EmailNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// F-01 新規登録 / F-02 ログイン・ログアウト。ログアウト（POST /api/auth/logout）は SecurityConfig の logout 設定で処理する
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final LoginSessionManager loginSessionManager;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
                          LoginSessionManager loginSessionManager) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.loginSessionManager = loginSessionManager;
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
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        loginSessionManager.start(authentication, httpRequest, httpResponse);
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // 失敗すると AuthenticationException が投げられ、GlobalExceptionHandler で 401 になる
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EmailNormalizer.normalize(request.email()), request.password()));
        loginSessionManager.start(authentication, httpRequest, httpResponse);
        return UserResponse.from((AuthUser) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return UserResponse.from(user);
    }
}
