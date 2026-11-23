package com.example.raisetime.security;

import com.example.raisetime.controller.AuthController;
import com.example.raisetime.logging.LogEvents;
import com.example.raisetime.logging.RequestLoggingFilter;
import com.example.raisetime.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 本番と同じ設定（CloudFront-Viewer-Address を使う）で、利用者の IP アドレスを使う場所が、
// すべてヘッダーの IP になることを、部品をつなげた本物の流れで確かめる（docs/テスト設計書.md D-6。T-40）。
// テストは dev プロファイル（ヘッダーを使わない）で動くので、本番と同じ値を指定する
@SpringBootTest(properties = "app.client-ip.header=CloudFront-Viewer-Address")
@AutoConfigureMockMvc
class ClientIpIntegrationTest {

    private static final String HEADER = "CloudFront-Viewer-Address";
    // ALB の IP（本番で getRemoteAddr() になるもの）
    private static final String ALB_IP = "10.0.1.23";

    @Autowired
    private MockMvc mockMvc;

    // ほかのテストと失敗の回数が混ざらないよう、テストごとに別の IP を使う（文書用の範囲 2001:db8::/32）
    private static String randomViewerIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "2001:db8:%x:%x:0:0:0:1".formatted(random.nextInt(0x10000), random.nextInt(0x10000));
    }

    private ResultActions login(String email, String viewerIp, String remoteAddr) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).with(request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        }).header(HEADER, viewerIp + ":54321")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"));
    }

    @Test
    void loginFailure_logsViewerIp_inLoginLogAndAccessLog() throws Exception {
        String viewerIp = randomViewerIp();

        try (LogCapture authLogs = LogCapture.of(AuthController.class);
             LogCapture accessLogs = LogCapture.of(RequestLoggingFilter.class)) {
            login("ip-" + UUID.randomUUID() + "@example.com", viewerIp, ALB_IP).andExpect(status().isUnauthorized());

            assertThat(LogCapture.fields(authLogs.single(LogEvents.LOGIN_FAILED))).containsEntry("client_ip", viewerIp);
            assertThat(LogCapture.fields(accessLogs.single(LogEvents.HTTP_REQUEST))).containsEntry("client_ip", viewerIp);
            assertThat(authLogs.allText() + accessLogs.allText()).doesNotContain(ALB_IP);
        }
    }

    @Test
    void signup_logsViewerIp() throws Exception {
        String viewerIp = randomViewerIp();
        String username = "ip" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        try (LogCapture authLogs = LogCapture.of(AuthController.class)) {
            mockMvc.perform(post("/api/auth/signup").with(csrf()).with(request -> {
                request.setRemoteAddr(ALB_IP);
                return request;
            }).header(HEADER, viewerIp + ":54321")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"name\":\"IP確認\",\"email\":\"" + username
                            + "@example.com\",\"password\":\"password123\"}"))
                    .andExpect(status().isCreated());

            assertThat(LogCapture.fields(authLogs.single(LogEvents.SIGNUP_SUCCEEDED))).containsEntry("client_ip", viewerIp);
        }
    }

    // 同じ IP の失敗回数は、ヘッダーの IP で数える。ALB の IP（getRemoteAddr()）が毎回同じでも、
    // 利用者ごとに分かれていること（同じ CloudFront を通る、関係ない人を巻き込まないこと）も確かめる
    @Test
    void failuresAreCountedPerViewerIp_notPerAlbIp() throws Exception {
        String attackerIp = randomViewerIp();
        String otherUserIp = randomViewerIp();

        try (LogCapture lockLogs = LogCapture.of(LoginAttemptService.class)) {
            for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_IP; i++) {
                login("ip-" + UUID.randomUUID() + "@example.com", attackerIp, ALB_IP).andExpect(status().isUnauthorized());
            }
            // 上限に達した IP は、別のメールアドレスでも止まる
            login("ip-" + UUID.randomUUID() + "@example.com", attackerIp, ALB_IP).andExpect(status().isTooManyRequests());
            // 同じ ALB・CloudFront を通っていても、別の利用者は止まらない
            login("ip-" + UUID.randomUUID() + "@example.com", otherUserIp, ALB_IP).andExpect(status().isUnauthorized());

            assertThat(LogCapture.fields(lockLogs.single(LogEvents.LOGIN_LOCKED)))
                    .containsEntry("locked_by", "ip")
                    .containsEntry("client_ip", attackerIp);
        }
    }
}
