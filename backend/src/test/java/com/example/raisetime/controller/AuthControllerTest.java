package com.example.raisetime.controller;

import com.example.raisetime.config.JwtConfig;
import com.example.raisetime.config.SecurityConfig;
import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.dto.LoginRequest;
import com.example.raisetime.dto.UserResponse;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.exception.GlobalExceptionHandler;
import com.example.raisetime.exception.InvalidRefreshTokenException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AccessTokenService;
import com.example.raisetime.security.AuthCookies;
import com.example.raisetime.security.AuthTokenIssuer;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.security.AuthUserDetailsService;
import com.example.raisetime.security.AuthUserJwtAuthenticationConverter;
import com.example.raisetime.security.CookieBearerTokenResolver;
import com.example.raisetime.security.ErrorResponseWriter;
import com.example.raisetime.security.JsonAccessDeniedHandler;
import com.example.raisetime.security.JsonAuthenticationEntryPoint;
import com.example.raisetime.service.AuthService;
import com.example.raisetime.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Security の設定（SecurityConfig・JwtConfig）まで含めて、認証 API の動きを確認する。
// JWT の発行・検証とパスワードの照合は本物を使い、DB（UserMapper）とリフレッシュトークンの保存だけモックにする
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class, AuthUserDetailsService.class,
        AccessTokenService.class, AuthUserJwtAuthenticationConverter.class, CookieBearerTokenResolver.class,
        AuthCookies.class, AuthTokenIssuer.class, ErrorResponseWriter.class, JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class})
class AuthControllerTest {

    private static final String PASSWORD = "password123";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final int ACCESS_TOKEN_SECONDS = 15 * 60;
    private static final int REFRESH_TOKEN_SECONDS = 7 * 24 * 60 * 60;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AccessTokenService accessTokenService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        User user = new User("taro_dev", "山田太郎", "taro@example.com", new BCryptPasswordEncoder().encode(PASSWORD));
        user.setId(1L);
        when(userMapper.findByEmail("taro@example.com")).thenReturn(user);
        when(refreshTokenService.issue(anyLong())).thenReturn(REFRESH_TOKEN);
        // login / refresh / me は、DB の最新のユーザー情報（名前・プロフィール画像）を返す
        when(authService.describe(1L)).thenReturn(Optional.of(new UserResponse("taro_dev", "山田太郎", null)));
        when(authService.describe(2L)).thenReturn(Optional.of(new UserResponse("newbie", "新人", null)));
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Cookie accessTokenCookie(String value) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, value);
    }

    // ---------- CSRF ----------

    // ほかのテストで使う csrf() は、CSRF フィルターの中の保存先をテスト用に差し替えたままにする。
    // その影響を受けずに本物の Cookie の発行を確認するため、このテストだけ新しいコンテキストで動かす
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void csrf_issuesXsrfTokenCookie_readableByJavaScript() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().sameSite("XSRF-TOKEN", "Lax"));
    }

    @Test
    void post_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------- アクセストークン（me） ----------

    @Test
    void me_withoutToken_returns401Json() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void otherApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/timeline"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidAccessTokenCookie_returnsUser() throws Exception {
        String token = accessTokenService.issue(new AuthUser(1L, "taro_dev", "山田太郎", null));

        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("taro_dev"))
                .andExpect(jsonPath("$.name").value("山田太郎"));
    }

    // 認証済みのリクエストのたびに CSRF トークンの Cookie が消されないこと（消えると次の POST が 403 になる）。
    // csrf() の影響を受けない本物の CSRF の保存先で確認するため、新しいコンテキストで動かす
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void me_withAccessToken_doesNotClearCsrfTokenCookie() throws Exception {
        String token = accessTokenService.issue(new AuthUser(1L, "taro_dev", "山田太郎", null));

        mockMvc.perform(get("/api/auth/me")
                        .cookie(accessTokenCookie(token), new Cookie("XSRF-TOKEN", "current-csrf-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"));
    }

    // ログイン中（access_token Cookie あり）でも、CSRF トークンのない変更系のリクエストは 403 にする。
    // トークンは Cookie でブラウザが自動で送るので、ここを省くと別サイトから勝手に操作されてしまう（issue #15）
    @Test
    void stateChangingRequest_whileLoggedIn_withoutCsrf_returns403() throws Exception {
        Cookie accessToken = accessTokenCookie(accessTokenService.issue(new AuthUser(1L, "taro_dev", "山田太郎", null)));

        for (var request : List.of(post("/api/posts"), put("/api/users/hanako/follow"),
                delete("/api/posts/1"))) {
            mockMvc.perform(request.cookie(accessToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void me_withTamperedToken_returns401() throws Exception {
        String token = accessTokenService.issue(new AuthUser(1L, "taro_dev", "山田太郎", null));
        // 署名の部分を書き換える
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_withExpiredToken_returns401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject("1")
                .issuedAt(past)
                .expiresAt(past.plusSeconds(ACCESS_TOKEN_SECONDS))
                .claim("username", "taro_dev")
                .claim("name", "山田太郎")
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withTokenInAuthorizationHeaderOnly_returns401() throws Exception {
        // アクセストークンは Cookie でだけ受け付ける
        String token = accessTokenService.issue(new AuthUser(1L, "taro_dev", "山田太郎", null));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- login ----------

    @Test
    void login_success_setsHttpOnlyTokenCookies_andRotatesCsrfToken_withoutSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("taro_dev"))
                .andExpect(jsonPath("$.name").value("山田太郎"))
                .andExpect(cookie().httpOnly(AuthCookies.ACCESS_TOKEN, true))
                .andExpect(cookie().path(AuthCookies.ACCESS_TOKEN, "/api"))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS_TOKEN, ACCESS_TOKEN_SECONDS))
                .andExpect(cookie().sameSite(AuthCookies.ACCESS_TOKEN, "Lax"))
                .andExpect(cookie().value(AuthCookies.REFRESH_TOKEN, REFRESH_TOKEN))
                .andExpect(cookie().httpOnly(AuthCookies.REFRESH_TOKEN, true))
                .andExpect(cookie().path(AuthCookies.REFRESH_TOKEN, "/api/auth"))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH_TOKEN, REFRESH_TOKEN_SECONDS))
                .andExpect(cookie().sameSite(AuthCookies.REFRESH_TOKEN, "Strict"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        verify(refreshTokenService).issue(1L);
        // サーバーはセッションを作らない
        assertThat(result.getRequest().getSession(false)).isNull();
        // レスポンスの JSON にトークンは入れない
        assertThat(result.getResponse().getContentAsString()).doesNotContain(REFRESH_TOKEN);
    }

    @Test
    void me_withAccessTokenFromLogin_returnsUser() throws Exception {
        String accessToken = login().getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getValue();

        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("taro_dev"));
    }

    @Test
    void login_emailIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("  Taro@Example.COM ", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void login_withStaleInvalidAccessTokenCookie_stillSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .cookie(accessTokenCookie("broken.token.value"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void login_wrongPassword_returns401WithoutTellingWhichIsWrong() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"))
                .andExpect(cookie().doesNotExist(AuthCookies.ACCESS_TOKEN));
        verify(refreshTokenService, never()).issue(anyLong());
    }

    @Test
    void login_unknownEmail_returnsSame401() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("nobody@example.com", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
    }

    @Test
    void login_blankFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    // ---------- refresh ----------

    @Test
    void refresh_success_rotatesRefreshToken_andIssuesNewAccessToken() throws Exception {
        when(refreshTokenService.rotate("old-refresh")).thenReturn(new RefreshTokenService.Rotation(1L, "new-refresh"));
        when(authService.findUser(1L)).thenReturn(Optional.of(new AuthUser(1L, "taro_dev", "山田太郎", null)));

        MvcResult result = mockMvc.perform(post("/api/auth/refresh").with(csrf())
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("taro_dev"))
                .andExpect(cookie().value(AuthCookies.REFRESH_TOKEN, "new-refresh"))
                .andExpect(cookie().exists(AuthCookies.ACCESS_TOKEN))
                .andReturn();

        String newAccessToken = result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getValue();
        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(newAccessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_withoutCookie_returns401_andClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ログインの有効期限が切れました。もう一度ログインしてください"))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH_TOKEN, 0));
    }

    @Test
    void refresh_invalidOrReusedToken_returns401_andClearsCookies() throws Exception {
        when(refreshTokenService.rotate("reused")).thenThrow(new InvalidRefreshTokenException("reused"));

        mockMvc.perform(post("/api/auth/refresh").with(csrf())
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, "reused")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(AuthCookies.REFRESH_TOKEN, 0));
    }

    @Test
    void refresh_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH_TOKEN, "old-refresh")))
                .andExpect(status().isForbidden());
        verify(refreshTokenService, never()).rotate(any());
    }

    // ---------- logout ----------

    @Test
    void logout_revokesRefreshToken_andClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf())
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, REFRESH_TOKEN)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookies.ACCESS_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH_TOKEN, 0));
        verify(refreshTokenService).revoke(REFRESH_TOKEN);
    }

    // ---------- signup ----------

    @Test
    void signup_success_returns201_andSetsTokenCookies() throws Exception {
        SignupRequest request = new SignupRequest("新人", "newbie", "new@example.com", PASSWORD);
        when(authService.signup(request)).thenReturn(new AuthUser(2L, "newbie", "新人", null));

        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newbie"))
                .andExpect(cookie().value(AuthCookies.REFRESH_TOKEN, REFRESH_TOKEN))
                .andReturn();
        verify(refreshTokenService).issue(2L);

        String accessToken = result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getValue();
        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newbie"));
    }

    @Test
    void signup_invalidInput_returns400WithFieldErrors() throws Exception {
        SignupRequest request = new SignupRequest("", "Bad-ID", "not-an-email", "short");

        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@.field == 'username')].message")
                        .value("@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください"));
        verify(authService, never()).signup(any());
    }

    @Test
    void signup_duplicate_returns409WithFieldErrors_andNoTokens() throws Exception {
        SignupRequest request = new SignupRequest("太郎", "taro_dev", "taro@example.com", PASSWORD);
        when(authService.signup(request)).thenThrow(new ConflictException(List.of(
                new FieldErrorResponse("username", "この@ユーザーIDはすでに使われています"))));

        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(cookie().doesNotExist(AuthCookies.ACCESS_TOKEN));
    }

    @Test
    void signup_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- プロフィール画像・削除されたユーザー（F-12） ----------

    @Test
    void me_returnsLatestProfileFromDb_includingAvatarUrl() throws Exception {
        when(authService.describe(1L))
                .thenReturn(Optional.of(new UserResponse("taro_new", "山田（改名）", "http://img/avatars/1/a.png")));
        Cookie token = accessTokenCookie(accessTokenService.issue(new AuthUser(1L, "taro_dev", "山田太郎", null)));

        mockMvc.perform(get("/api/auth/me").cookie(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("taro_new"))
                .andExpect(jsonPath("$.name").value("山田（改名）"))
                .andExpect(jsonPath("$.avatarUrl").value("http://img/avatars/1/a.png"));
    }

    @Test
    void me_deletedUser_returns401_evenWithValidAccessToken() throws Exception {
        when(authService.describe(9L)).thenReturn(Optional.empty());
        Cookie token = accessTokenCookie(accessTokenService.issue(new AuthUser(9L, "gone", "消えた人", null)));

        mockMvc.perform(get("/api/auth/me").cookie(token))
                .andExpect(status().isUnauthorized());
    }
}
