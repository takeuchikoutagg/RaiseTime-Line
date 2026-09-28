package com.example.raisetime.controller;

import com.example.raisetime.config.SecurityConfig;
import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.dto.LoginRequest;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.exception.GlobalExceptionHandler;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.security.AuthUserDetailsService;
import com.example.raisetime.security.ErrorResponseWriter;
import com.example.raisetime.security.JsonAccessDeniedHandler;
import com.example.raisetime.security.JsonAuthenticationEntryPoint;
import com.example.raisetime.security.LoginSessionManager;
import com.example.raisetime.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Security の設定（SecurityConfig）まで含めて、認証 API の動きを確認する。
// ログインは本物の AuthUserDetailsService と BCrypt を使い、DB（UserMapper）だけをモックにする
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthUserDetailsService.class, LoginSessionManager.class,
        ErrorResponseWriter.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class AuthControllerTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserMapper userMapper;

    @BeforeEach
    void setUpUser() {
        User user = new User("taro_dev", "山田太郎", "taro@example.com", new BCryptPasswordEncoder().encode(PASSWORD));
        user.setId(1L);
        when(userMapper.findByEmail("taro@example.com")).thenReturn(user);
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
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

    // ---------- me ----------

    @Test
    void me_withoutLogin_returns401Json() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void otherApi_withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/timeline"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- login ----------

    @Test
    void login_success_returnsUser_storesContextInSession_andRotatesCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("taro_dev"))
                .andExpect(jsonPath("$.name").value("山田太郎"))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        org.hamcrest.Matchers.notNullValue()))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void login_emailIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("  Taro@Example.COM ", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void login_wrongPassword_returns401WithoutTellingWhichIsWrong() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("taro@example.com", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
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

    @Test
    void me_afterLogin_returnsUser_withoutPasswordHashInSession() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("taro_dev"))
                .andExpect(jsonPath("$.password").doesNotExist());

        var context = (org.springframework.security.core.context.SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        org.assertj.core.api.Assertions.assertThat(((AuthUser) context.getAuthentication().getPrincipal())
                .getPassword()).isNull();
    }

    // ---------- logout ----------

    @Test
    void logout_returns204_andSessionNoLongerAuthenticated() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    // ---------- signup ----------

    @Test
    void signup_success_returns201_andIsLoggedIn() throws Exception {
        SignupRequest request = new SignupRequest("新人", "newbie", "new@example.com", PASSWORD);
        when(authService.signup(request)).thenReturn(new AuthUser(2L, "newbie", "新人", null));

        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newbie"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/me").session(session))
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
    void signup_duplicate_returns409WithFieldErrors() throws Exception {
        SignupRequest request = new SignupRequest("太郎", "taro_dev", "taro@example.com", PASSWORD);
        when(authService.signup(request)).thenThrow(new ConflictException(List.of(
                new FieldErrorResponse("username", "この@ユーザーIDはすでに使われています"))));

        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, nullValue()));
    }

    @Test
    void signup_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
