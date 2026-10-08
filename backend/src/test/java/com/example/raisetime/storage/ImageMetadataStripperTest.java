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

    // ---------- テスト技法から足したケース（docs/テスト設計書.md T-22。分岐網羅 100% を目標にする） ----------
    // 利用者が送ってくる、信用できないデータを読む処理なので、壊れ方ごとに「400 用の例外になる」ことを確かめる

    private static byte[] bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }

    private static void assertInvalid(byte[] data, ImageType type) {
        assertThatThrownBy(() -> ImageMetadataStripper.strip(data, type))
                .isInstanceOf(ImageMetadataStripper.InvalidImageException.class);
    }

    @Test
    void jpeg_paddingAndMarkersWithoutLength_areHandled() {
        // 埋め草の FF は読み飛ばす
        assertThat(ImageMetadataStripper.strip(bytes(0xFF, 0xD8, 0xFF, 0xFF, 0xD9), ImageType.JPEG))
                .containsExactly(bytes(0xFF, 0xD8, 0xFF, 0xD9));
        // 長さを持たないマーカー（RST0・RST7・TEM）は、そのまま残す
        byte[] withRst = bytes(0xFF, 0xD8, 0xFF, 0xD0, 0xFF, 0xD7, 0xFF, 0x01, 0xFF, 0xD9);
        assertThat(ImageMetadataStripper.strip(withRst, ImageType.JPEG)).containsExactly(withRst);
        // SOS・EOI が無いまま終わっても、読めたところまでを返す（EXIF は消す）
        byte[] noEnd = bytes(0xFF, 0xD8, 0xFF, 0xE1, 0x00, 0x03, 0x41, 0xFF, 0xE0, 0x00, 0x02);
        assertThat(ImageMetadataStripper.strip(noEnd, ImageType.JPEG))
                .containsExactly(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x02));
    }

    @Test
    void jpeg_brokenStructures_areRejected() {
        // マーカー（FF）が無い
        assertInvalid(bytes(0xFF, 0xD8, 0x00, 0x00), ImageType.JPEG);
        // セグメントの長さが 2 より小さい（長さには、長さ自身の2バイトを含む決まり）
        assertInvalid(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x01), ImageType.JPEG);
        // マーカーの途中で切れている
        assertInvalid(bytes(0xFF, 0xD8, 0xFF), ImageType.JPEG);
        // 長さの途中で切れている
        assertInvalid(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00), ImageType.JPEG);
    }

    @Test
    void png_brokenChunkLengths_areRejected() {
        int[] signature = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        // 長さが負（最上位のビットが立っている）
        assertInvalid(concat(bytes(signature), bytes(0xFF, 0xFF, 0xFF, 0xFF, 'I', 'D', 'A', 'T')), ImageType.PNG);
        // 長さが大きすぎて、足し算があふれる（約21億）
        assertInvalid(concat(bytes(signature), bytes(0x7F, 0xFF, 0xFF, 0xFF, 'I', 'D', 'A', 'T', 0, 0, 0, 0)),
                ImageType.PNG);
        assertInvalid(concat(bytes(signature), bytes(0x7F, 0xFF, 0xFF, 0xFF, 't', 'E', 'X', 't', 0, 0, 0, 0)),
                ImageType.PNG);
        // 長さの途中で切れている
        assertInvalid(concat(bytes(signature), bytes(0, 0)), ImageType.PNG);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // 種類（4文字）＋大きさ（リトルエンディアン。実際の中身と違う値も書ける）＋中身
    private static byte[] rawWebpChunk(String type, int declaredSize, int... payload) {
        ByteBuffer out = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(type.getBytes(StandardCharsets.ISO_8859_1)).putInt(declaredSize).put(bytes(payload));
        return out.array();
    }

    @Test
    void webp_emptyExtendedHeader_andOddSizedChunk_areKept() {
        // VP8X の中身が空（フラグが無い）ときは、そのまま残す
        byte[] emptyVp8x = webp(rawWebpChunk("VP8X", 0), rawWebpChunk("VP8 ", 2, 1, 2));
        assertThat(ImageMetadataStripper.strip(emptyVp8x, ImageType.WEBP)).containsExactly(emptyVp8x);
        // 大きさが奇数のチャンクは、1バイトの詰め物ごと残す。最後のチャンクで詰め物が無くても読める
        byte[] odd = webp(rawWebpChunk("VP8 ", 3, 1, 2, 3, 0), rawWebpChunk("ALPH", 1, 9));
        assertThat(ImageMetadataStripper.strip(odd, ImageType.WEBP)).containsExactly(odd);
    }

    @Test
    void webp_brokenChunkSizes_areRejected() {
        // 大きさが負
        assertInvalid(webp(rawWebpChunk("VP8 ", -1, 1, 2)), ImageType.WEBP);
        // 大きさが、実際のデータより大きい
        assertInvalid(webp(rawWebpChunk("VP8 ", 100, 1, 2)), ImageType.WEBP);
        // 大きさが大きすぎて、足し算があふれる（約21億）。残すチャンクでも、消すチャンク（EXIF）でも
        assertInvalid(webp(rawWebpChunk("VP8 ", Integer.MAX_VALUE, 1, 2)), ImageType.WEBP);
        assertInvalid(webp(rawWebpChunk("EXIF", Integer.MAX_VALUE, 1, 2)), ImageType.WEBP);
        assertInvalid(webp(rawWebpChunk("VP8 ", Integer.MAX_VALUE - 7, 1, 2)), ImageType.WEBP);
        // チャンクの途中（種類・大きさ）で切れている
        assertInvalid(webp(bytes('V', 'P', '8')), ImageType.WEBP);
        assertInvalid(webp(bytes('V', 'P', '8', ' ', 1, 0)), ImageType.WEBP);
    }
}
