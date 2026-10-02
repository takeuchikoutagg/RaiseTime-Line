package com.example.raisetime.storage;

// 投稿画像の保存先。本番は AWS S3、開発は S3 互換の S3Mock（どちらも S3ImageStorage が扱う）
public interface ImageStorage {

    void put(String key, byte[] data, String contentType);

    // 無いキーを消しても失敗にしない
    void delete(String key);

    // 画面で画像を表示するための URL
    String publicUrl(String key);
}
