package com.example.raisetime.storage;

import com.example.raisetime.config.StorageProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final StorageProperties properties;

    public S3ImageStorage(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    // URL（バケット名やドメイン）は DB に保存せず、表示するときにキーから組み立てる（docs/ER図.md の post_images）
    @Override
    public String publicUrl(String key) {
        String base = properties.publicBaseUrl();
        return (base.endsWith("/") ? base : base + "/") + key;
    }
}
