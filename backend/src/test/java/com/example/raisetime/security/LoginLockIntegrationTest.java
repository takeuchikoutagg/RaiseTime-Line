package com.example.raisetime.security;

import com.example.raisetime.logging.LogEvents;
import com.example.raisetime.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ログインのロック（状態遷移）を、部品をつなげた本物の流れで確かめる（docs/テスト設計書.md 2.6）。
// AuthControllerTest は LoginAttemptService をモックにしているので、
// 「5回失敗 → 6回目は 429 → ロックのログが1回だけ出る」というつながりは、ここでしか確かめられない
@SpringBootTest
@AutoConfigureMockMvc
class LoginLockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ほかのテストと回数が混ざらないよう、このテストだけのメールアドレスと IP を使う
    private final String email = "lock-" + UUID.randomUUID() + "@example.com";
    private final String ip = "198.51.100." + (1 + (int) (Math.random() * 250));

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void fiveFailures_thenLocked_andLockIsLoggedOnce() throws Exception {
        try (LogCapture lockLogs = LogCapture.of(LoginAttemptService.class)) {
            for (int i = 1; i <= LoginAttemptService.MAX_FAILURES_PER_EMAIL; i++) {
                login("wrong-password").andExpect(status().isUnauthorized());
            }
            // 6回目からは、パスワードを確かめずに 429
            login("wrong-password").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
            login("another-wrong-password").andExpect(status().isTooManyRequests());

            // ロックした瞬間に1回だけ記録する（止めている間は、アクセスログの 429 でわかる）
            assertThat(lockLogs.count(LogEvents.LOGIN_LOCKED)).isEqualTo(1);
            assertThat(LogCapture.fields(lockLogs.single(LogEvents.LOGIN_LOCKED)))
                    .containsEntry("locked_by", "email")
                    .containsEntry("client_ip", ip)
                    .containsEntry("failure_count", LoginAttemptService.MAX_FAILURES_PER_EMAIL);
            assertThat(lockLogs.allText()).doesNotContain(email, "wrong-password");
        }
    }
}
