package com.example.raisetime.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
        if (properties.usesCustomEndpoint()) {
            // 開発の S3Mock：認証しないのでダミーの鍵を使う。URL は http://ホスト/バケット/キー の形（path style）
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .forcePathStyle(true)
                    .credentialsProvider(
                            StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")));
        } else {
            // 本番の AWS S3：EC2 の IAM ロールなど、環境から権限を得る（アクセスキーはコードにも設定にも置かない）
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }
}
