package com.example.raisetime.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    // 各形式の先頭のバイト（マジックナンバー）＋適当なデータ
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};
    static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    @Test
    void detect_supportedFormats() {
        assertThat(ImageType.detect(JPEG)).contains(ImageType.JPEG);
        assertThat(ImageType.detect(PNG)).contains(ImageType.PNG);
        assertThat(ImageType.detect(GIF)).contains(ImageType.GIF);
        assertThat(ImageType.detect(WEBP)).contains(ImageType.WEBP);
    }

    @Test
    void detect_rejectsNonImagesAndUnsupportedFormats() {
        // 拡張子を .jpg に変えただけのテキストファイル、などを想定
        assertThat(ImageType.detect("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        // RIFF だが WebP ではない（WAV 音声）
        assertThat(ImageType.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        // BMP は対象外
        assertThat(ImageType.detect("BM\0\0\0\0".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }

    @Test
    void contentTypeAndExtension() {
        assertThat(ImageType.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.WEBP.contentType()).isEqualTo("image/webp");
    }
}
