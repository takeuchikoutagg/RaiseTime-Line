package com.example.raisetime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.properties の app.storage.*（投稿画像の保存先）
@ConfigurationProperties("app.storage")
public record StorageProperties(
        String bucket,
        String region,
        // S3 互換サービス（開発の S3Mock）の URL。空なら AWS S3 を使う
        String endpoint,
        // 画像を表示するときの URL の頭の部分。この後ろに「/キー」を付ける
        String publicBaseUrl
) {

    public boolean usesCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
