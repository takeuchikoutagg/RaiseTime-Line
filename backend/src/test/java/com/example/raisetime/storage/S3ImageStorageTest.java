package com.example.raisetime.storage;

import com.example.raisetime.config.StorageConfig;
import com.example.raisetime.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Docker Compose の S3Mock（http://localhost:9090、docker compose up -d で起動）に対して、実際に保存・削除する
class S3ImageStorageTest {

    private static final StorageProperties PROPERTIES = new StorageProperties(
            "raisetime-images", "ap-northeast-1", "http://localhost:9090", "http://localhost:9090/raisetime-images");

    private final HttpClient http = HttpClient.newHttpClient();
    private S3ImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3ImageStorage(new StorageConfig().s3Client(PROPERTIES), PROPERTIES);
    }

    private HttpResponse<byte[]> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void put_thenPublicUrlServesTheImage_thenDeleteRemovesIt() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".png";
        byte[] data = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

        storage.put(key, data, "image/png");

        String url = storage.publicUrl(key);
        assertThat(url).isEqualTo("http://localhost:9090/raisetime-images/" + key);
        HttpResponse<byte[]> response = get(url);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(data);
        assertThat(response.headers().firstValue("Content-Type")).contains("image/png");

        storage.delete(key);
        assertThat(get(url).statusCode()).isEqualTo(404);
    }

    @Test
    void delete_missingKey_doesNotFail() {
        assertThatCode(() -> storage.delete("test/does-not-exist.png")).doesNotThrowAnyException();
    }

    @Test
    void publicUrl_handlesTrailingSlashInBaseUrl() {
        StorageProperties withSlash = new StorageProperties("b", "ap-northeast-1", "", "https://cdn.example.com/images/");
        S3ImageStorage s = new S3ImageStorage(null, withSlash);

        assertThat(s.publicUrl("posts/1/a.png")).isEqualTo("https://cdn.example.com/images/posts/1/a.png");
    }
}
