package com.example.raisetime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// API 仕様書（docs/api/openapi.yaml）が、今のコードから作られる内容と同じかを確かめる。
// API を変えたのに作り直し忘れると、このテストが失敗する。
// 作り直すには `./gradlew generateOpenApi`（openapi.write=true でこのテストを動かし、ファイルに書き出す）
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentTest {

    // テストは backend/ で動くので、リポジトリ直下の docs/ は1つ上
    private static final Path DOCUMENT = Path.of("..", "docs", "api", "openapi.yaml");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocument_isUpToDate() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if (Boolean.getBoolean("openapi.write")) {
            Files.createDirectories(DOCUMENT.getParent());
            Files.writeString(DOCUMENT, generated, StandardCharsets.UTF_8);
            return;
        }

        assertThat(read())
                .as("docs/api/openapi.yaml が古くなっています。`./gradlew generateOpenApi` で作り直してください")
                .isEqualTo(generated);
    }

    // 開発では、ログインしなくても Swagger UI を開ける
    @Test
    void swaggerUi_isOpenWithoutLoginInDev() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private static String read() throws IOException {
        return Files.exists(DOCUMENT) ? Files.readString(DOCUMENT, StandardCharsets.UTF_8) : "";
    }
}
