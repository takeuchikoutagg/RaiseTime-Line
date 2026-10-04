package com.example.raisetime.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageMetadataStripperTest {

    // 「GPS」という文字を含む、位置情報のつもりのデータ
    private static final byte[] GPS = "Exif\0\0GPSLatitude=35.6812;GPSLongitude=139.7671".getBytes(StandardCharsets.ISO_8859_1);

    private static byte[] encode(String format) throws IOException {
        BufferedImage image = new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 24; y++) {
                image.setRGB(x, y, (x * 8) << 16 | (y * 10) << 8 | 0x80);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static boolean contains(byte[] data, String text) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(text);
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    // SOI のすぐあとに、指定したマーカーのセグメントを差し込む
    private static byte[] insertJpegSegment(byte[] jpeg, int marker, byte[] payload) {
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + 4 + payload.length);
        out.put(jpeg, 0, 2);
        out.put((byte) 0xFF).put((byte) marker).putShort((short) (payload.length + 2)).put(payload);
        out.put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    // 8バイトの目印のすぐあと（IHDR の前）は不正なので、IHDR のあとにチャンクを差し込む
    private static byte[] insertPngChunk(byte[] png, String type, byte[] payload) {
        int afterIhdr = 8 + 12 + ByteBuffer.wrap(png, 8, 4).getInt();
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        ByteBuffer out = ByteBuffer.allocate(png.length + 12 + payload.length);
        out.put(png, 0, afterIhdr);
        out.putInt(payload.length).put(typeBytes).put(payload).putInt((int) crc.getValue());
        out.put(png, afterIhdr, png.length - afterIhdr);
        return out.array();
    }

    @Test
    void jpeg_exifXmpIptcAndComments_areRemoved_andImageStillDecodes() throws IOException {
        byte[] jpeg = encode("jpg");
        byte[] withMetadata = insertJpegSegment(insertJpegSegment(insertJpegSegment(jpeg,
                0xE1, GPS), 0xED, "Photoshop IPTC GPS".getBytes(StandardCharsets.ISO_8859_1)),
                0xFE, "comment GPS".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(contains(withMetadata, "GPS")).isTrue();

        byte[] stripped = ImageMetadataStripper.strip(withMetadata, ImageType.JPEG);

        assertThat(contains(stripped, "GPS")).isFalse();
        // 画素のデータは変わらない（元の JPEG とまったく同じになる）
        assertThat(stripped).isEqualTo(jpeg);
        assertThat(decode(stripped).getWidth()).isEqualTo(32);
    }

    @Test
    void png_exifAndTextChunks_areRemoved_andImageStillDecodes() throws IOException {
        byte[] png = encode("png");
        byte[] withMetadata = insertPngChunk(insertPngChunk(png, "eXIf", GPS), "tEXt",
                "Comment\0GPS here".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(contains(withMetadata, "GPS")).isTrue();

        byte[] stripped = ImageMetadataStripper.strip(withMetadata, ImageType.PNG);

        assertThat(contains(stripped, "GPS")).isFalse();
        assertThat(stripped).isEqualTo(png);
        assertThat(decode(stripped).getHeight()).isEqualTo(24);
    }

    private static byte[] webpChunk(String type, byte[] payload) {
        int padded = payload.length + (payload.length % 2);
        ByteBuffer out = ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN);
        out.put(type.getBytes(StandardCharsets.ISO_8859_1)).putInt(payload.length).put(payload);
        return out.array();
    }

    private static byte[] webp(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            body.writeBytes(chunk);
        }
        byte[] content = body.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(12 + content.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.ISO_8859_1)).putInt(4 + content.length)
                .put("WEBP".getBytes(StandardCharsets.ISO_8859_1)).put(content);
        return out.array();
    }

    @Test
    void webp_exifAndXmpChunks_areRemoved_flagsCleared_andRiffSizeFixed() {
        // VP8X のフラグ：EXIF（0x08）と XMP（0x04）あり
        byte[] vp8x = {0x0C, 0, 0, 0, 31, 0, 0, 23, 0, 0};
        byte[] image = {1, 2, 3, 4, 5};
        byte[] withMetadata = webp(webpChunk("VP8X", vp8x), webpChunk("VP8L", image), webpChunk("EXIF", GPS),
                webpChunk("XMP ", "<x:GPS/>".getBytes(StandardCharsets.ISO_8859_1)));

        byte[] stripped = ImageMetadataStripper.strip(withMetadata, ImageType.WEBP);

        assertThat(contains(stripped, "GPS")).isFalse();
        byte[] expectedVp8x = vp8x.clone();
        expectedVp8x[0] = 0;
        assertThat(stripped).isEqualTo(webp(webpChunk("VP8X", expectedVp8x), webpChunk("VP8L", image)));
        assertThat(ImageType.detect(stripped)).contains(ImageType.WEBP);
    }

    @Test
    void gif_isReturnedAsIs() {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};

        assertThat(ImageMetadataStripper.strip(gif, ImageType.GIF)).isSameAs(gif);
    }

    @Test
    void brokenImages_areRejected() {
        // 長さが実際のデータより長い
        byte[] brokenJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 1, 2};
        byte[] brokenPng = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D, 'I', 'H'};

        assertThatThrownBy(() -> ImageMetadataStripper.strip(brokenJpeg, ImageType.JPEG))
                .isInstanceOf(ImageMetadataStripper.InvalidImageException.class);
        assertThatThrownBy(() -> ImageMetadataStripper.strip(brokenPng, ImageType.PNG))
                .isInstanceOf(ImageMetadataStripper.InvalidImageException.class);
    }
}
