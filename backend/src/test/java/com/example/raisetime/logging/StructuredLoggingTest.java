package com.example.raisetime.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 本番の設定（JSON の構造化ログ）で、ログが JSON になり、監視で使う項目が入ることを確かめる
// （docs/ログ・監視設計.md 第2部 3.1）。テストは dev プロファイルで動くので、本番と同じ値を指定する
@SpringBootTest(properties = {
    "logging.structured.format.console=logstash",
    "APP_ENV=test-env",
    "APP_VERSION=abc1234"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void accessLog_isOneJsonLine_withCommonFieldsAndRequestId(CapturedOutput output) throws Exception {
        String requestId = mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);

        String line = output.getOut().lines()
                .filter(l -> l.contains("\"event\":\"" + LogEvents.HTTP_REQUEST + "\""))
                .findFirst().orElseThrow(() -> new AssertionError("アクセスログがありません:\n" + output.getOut()));
        JsonNode json = objectMapper.readTree(line);

        // すべてのログに付く項目
        assertThat(json.get("service").asString()).isEqualTo("raisetime-backend");
        assertThat(json.get("env").asString()).isEqualTo("test-env");
        assertThat(json.get("version").asString()).isEqualTo("abc1234");
        assertThat(json.get("level").asString()).isEqualTo("INFO");
        assertThat(json.has("@timestamp")).isTrue();
        assertThat(json.get("message").asString()).contains("GET /api/posts/1 401");
        // リクエストごとの項目（文字ではなく、数値は数値として出る）
        assertThat(json.get(LogContext.REQUEST_ID).asString()).isEqualTo(requestId);
        assertThat(json.get("method").asString()).isEqualTo("GET");
        assertThat(json.get("path").asString()).isEqualTo("/api/posts/1");
        assertThat(json.get("status").isNumber()).isTrue();
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("duration_ms").isNumber()).isTrue();
    }

    // 本番（プロファイルなし）は JSON、開発（dev プロファイル）は文章、という設定になっていること
    @Test
    void productionDefaultsToJson_andDevProfileTurnsItOff() throws Exception {
        Properties production = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        Properties dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));

        assertThat(production.getProperty("logging.structured.format.console")).isEqualTo("logstash");
        assertThat(dev.getProperty("logging.structured.format.console")).isEmpty();
    }
}
