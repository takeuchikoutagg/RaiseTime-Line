package com.example.raisetime.security;

import ch.qos.logback.classic.Level;
import com.example.raisetime.exception.GlobalExceptionHandler;
import com.example.raisetime.logging.LogEvents;
import com.example.raisetime.logging.RequestLoggingFilter;
import com.example.raisetime.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 本文の大きさの上限を、フィルターをつなげた本物の流れで確かめる（docs/テスト設計書.md D-7。T-43）。
// RequestBodyLimitFilterTest はフィルター1つだけなので、「アクセスログの内側・Spring Security より前」という順番は、ここで確かめる
@SpringBootTest
@AutoConfigureMockMvc
class RequestBodyLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void largeJson_isRejectedBeforeCsrfAndLogin_andOnlyAccessLogRecordsIt() throws Exception {
        String hugeJson = "{\"email\":\"" + "a".repeat(RequestBodyLimitFilter.MAX_BODY_BYTES) + "\",\"password\":\"x\"}";

        try (LogCapture accessLogs = LogCapture.of(RequestLoggingFilter.class);
             LogCapture errorLogs = LogCapture.of(GlobalExceptionHandler.class);
             LogCapture deniedLogs = LogCapture.of(JsonAccessDeniedHandler.class)) {
            // CSRF トークンもログインも無い。それでも、本文を読む前に 413 で止まる（CSRF の 403 にならない）
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(hugeJson))
                    .andExpect(status().isContentTooLarge())
                    .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                    .andExpect(jsonPath("$.message").value(RequestBodyLimitFilter.MESSAGE));

            // アクセスログには 413 として残る（件数は status で見る）
            assertThat(LogCapture.fields(accessLogs.single(LogEvents.HTTP_REQUEST)))
                    .containsEntry("path", "/api/auth/login")
                    .containsEntry("status", 413);
            // 利用者の誤りなので、WARN・ERROR は出さない。本文もログに出さない
            assertThat(Stream.of(accessLogs, errorLogs, deniedLogs).flatMap(logs -> logs.events().stream()))
                    .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
            assertThat(accessLogs.allText()).doesNotContain("aaaaaaaaaa");
        }
    }

    @Test
    void imagePost_withLargeBody_isNotLimitedHere() throws Exception {
        MockMultipartFile image = new MockMultipartFile("images", "big.png", "image/png",
                new byte[RequestBodyLimitFilter.MAX_BODY_BYTES * 2]);

        // 画像つきの投稿は上限の対象外なので、413 ではなく、その先のログインの確認（401）まで進む
        mockMvc.perform(multipart("/api/posts").file(image).param("body", "x").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
