package com.example.raisetime.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

// 画像の保存先（S3）へのつなぎ方。開発（S3Mock）と本番（AWS S3）で分かれる。
// 本番の分かれ道は、開発・ほかのテストでは一度も通らないので、ここで確かめる（docs/テスト設計書.md T-27）
class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    private static StorageProperties properties(String endpoint) {
        return new StorageProperties("raisetime-images", "ap-northeast-1", endpoint, "https://example.com/images");
    }

    @Test
    void usesCustomEndpoint_onlyWhenEndpointHasText() {
        assertThat(properties("http://localhost:9090").usesCustomEndpoint()).isTrue();
        // 本番は STORAGE_ENDPOINT を空にする
        assertThat(properties("").usesCustomEndpoint()).isFalse();
        assertThat(properties("   ").usesCustomEndpoint()).isFalse();
        assertThat(properties(null).usesCustomEndpoint()).isFalse();
    }

    @Test
    void development_connectsToTheCustomEndpoint() {
        try (S3Client client = config.s3Client(properties("http://localhost:9090"))) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).contains(URI.create("http://localhost:9090"));
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.AP_NORTHEAST_1);
        }
    }

    // 本番：URL を上書きせず、AWS S3 につなぐ。権限は環境（EC2 の IAM ロールなど）から得る。
    // クライアントを作るだけなら AWS には通信しない
    @Test
    void production_connectsToAwsS3_withoutEndpointOverride() {
        try (S3Client client = config.s3Client(properties(""))) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.AP_NORTHEAST_1);
            assertThat(client.serviceClientConfiguration().credentialsProvider()).isNotNull();
        }
    }
}
