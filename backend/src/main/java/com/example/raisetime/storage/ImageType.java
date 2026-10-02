package com.example.raisetime.storage;

import java.util.Arrays;
import java.util.Optional;

// 投稿できる画像の種類。ファイル名や Content-Type は書き換えられるので、ファイルの先頭のバイト（マジックナンバー）で判定する
public enum ImageType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MARK = {'W', 'E', 'B', 'P'};

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] data) {
        if (startsWith(data, 0, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(data, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(data, 0, GIF_MAGIC)) {
            return Optional.of(GIF);
        }
        // WebP は「RIFF」＋4バイト（サイズ）＋「WEBP」で始まる
        if (startsWith(data, 0, RIFF) && startsWith(data, 8, WEBP_MARK)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
