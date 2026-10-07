package com.example.raisetime.storage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

// 画像から、撮影場所（GPS）などが入っている付加情報（メタデータ）を取り除く（投稿の画像・プロフィール画像）。
// スマホの写真には EXIF に緯度・経度が入っていることがあり、そのまま保存すると、誰でもダウンロードして家の場所などを読めてしまう。
// 画面でも作り直してから送るが、API を直接呼ばれたときのために、サーバーでも保存する前に必ず取り除く。
// 画素（見た目）のデータには触らず、メタデータの部分だけを抜く
// - JPEG：APP1（EXIF・XMP）・APP13（IPTC）・COM（コメント）のセグメント
// - PNG ：eXIf・tEXt・zTXt・iTXt のチャンク
// - WebP：EXIF・XMP のチャンク（と、VP8X の「EXIF・XMP あり」のフラグ）
// - GIF ：位置情報を入れる仕組みがないので、そのまま
public final class ImageMetadataStripper {

    private static final int JPEG_APP1 = 0xE1;
    private static final int JPEG_APP13 = 0xED;
    private static final int JPEG_COM = 0xFE;
    private static final int JPEG_SOS = 0xDA;
    private static final int JPEG_EOI = 0xD9;
    private static final Set<String> PNG_METADATA_CHUNKS = Set.of("eXIf", "tEXt", "zTXt", "iTXt");
    private static final Set<String> WEBP_METADATA_CHUNKS = Set.of("EXIF", "XMP ");
    private static final int WEBP_FLAG_EXIF = 0x08;
    private static final int WEBP_FLAG_XMP = 0x04;

    private ImageMetadataStripper() {
    }

    // 中身が壊れていて読み取れないときは InvalidImageException（呼び出し元で「画像を選んでください」の 400 にする）
    public static byte[] strip(byte[] data, ImageType type) {
        try {
            return switch (type) {
                case JPEG -> stripJpeg(data);
                case PNG -> stripPng(data);
                case WEBP -> stripWebp(data);
                case GIF -> data;
            };
        } catch (IndexOutOfBoundsException e) {
            throw new InvalidImageException("画像の中身を読み取れませんでした", e);
        }
    }

    // JPEG は「FF xx（マーカー）＋長さ＋中身」のセグメントが並び、SOS（画像データの始まり）のあとは最後まで画像データ
    private static byte[] stripJpeg(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 2); // SOI（FF D8）
        int i = 2;
        while (i < data.length) {
            if ((data[i] & 0xFF) != 0xFF) {
                throw new InvalidImageException("JPEG のマーカーがありません");
            }
            int marker = data[i + 1] & 0xFF;
            if (marker == 0xFF) {
                // 埋め草の FF は読み飛ばす
                i++;
                continue;
            }
            if (marker == JPEG_SOS || marker == JPEG_EOI) {
                out.write(data, i, data.length - i);
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                // 長さを持たないマーカー
                out.write(data, i, 2);
                i += 2;
                continue;
            }
            int length = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
            int end = i + 2 + length;
            if (length < 2 || end > data.length) {
                throw new InvalidImageException("JPEG のセグメントの長さが正しくありません");
            }
            if (marker != JPEG_APP1 && marker != JPEG_APP13 && marker != JPEG_COM) {
                out.write(data, i, end - i);
            }
            i = end;
        }
        return out.toByteArray();
    }

    // PNG は 8 バイトの目印のあとに「長さ（4）＋種類（4）＋中身＋CRC（4）」のチャンクが並ぶ
    private static byte[] stripPng(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 8);
        int i = 8;
        while (i < data.length) {
            int length = ByteBuffer.wrap(data, i, 4).getInt();
            // 「i + 12 + length > data.length」と書くと、length がとても大きいときに足し算があふれて負になり、
            // チェックをすり抜ける。残りの大きさと比べる形にする
            if (length < 0 || length > data.length - i - 12) {
                throw new InvalidImageException("PNG のチャンクの長さが正しくありません");
            }
            int end = i + 12 + length;
            String chunkType = new String(data, i + 4, 4, StandardCharsets.ISO_8859_1);
            if (!PNG_METADATA_CHUNKS.contains(chunkType)) {
                out.write(data, i, end - i);
            }
            i = end;
        }
        return out.toByteArray();
    }

    // WebP は「RIFF」＋全体の大きさ（4、リトルエンディアン）＋「WEBP」のあとに「種類（4）＋大きさ（4）＋中身（偶数にそろえる）」のチャンクが並ぶ
    private static byte[] stripWebp(byte[] data) {
        ByteArrayOutputStream body = new ByteArrayOutputStream(data.length);
        int i = 12;
        while (i < data.length) {
            String chunkType = new String(data, i, 4, StandardCharsets.ISO_8859_1);
            int size = ByteBuffer.wrap(data, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            // 足し算があふれないよう、残りの大きさと比べる（PNG と同じ）。
            // あふれるとチェックをすり抜け、下の copyOfRange が想定外の例外（500）を投げていた
            if (size < 0 || size > data.length - i - 8) {
                throw new InvalidImageException("WebP のチャンクの大きさが正しくありません");
            }
            // 大きさが奇数なら、1バイトの詰め物が続く（最後のチャンクでは無いこともある）
            int end = Math.min(i + 8 + size + (size % 2), data.length);
            if (!WEBP_METADATA_CHUNKS.contains(chunkType)) {
                byte[] chunk = Arrays.copyOfRange(data, i, end);
                if (chunkType.equals("VP8X") && size > 0) {
                    // 拡張ヘッダーの「EXIF あり」「XMP あり」のフラグを消す
                    chunk[8] = (byte) (chunk[8] & ~(WEBP_FLAG_EXIF | WEBP_FLAG_XMP));
                }
                body.write(chunk, 0, chunk.length);
            }
            i = end;
        }
        byte[] chunks = body.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(12 + chunks.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(data, 0, 4); // RIFF
        out.putInt(4 + chunks.length); // 「WEBP」＋チャンク の大きさ
        out.put(data, 8, 4); // WEBP
        out.put(chunks);
        return out.array();
    }

    // 画像の中身が壊れていて、メタデータを取り除けないとき
    public static class InvalidImageException extends RuntimeException {

        public InvalidImageException(String message) {
            super(message);
        }

        public InvalidImageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
