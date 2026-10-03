package com.example.raisetime.dto;

import java.time.Instant;

// コメント1件（docs/機能定義書/F-05_コメント.md）
public record CommentResponse(Long id, String body, Instant createdAt, Author author) {

    // avatarUrl：プロフィール画像の URL（F-12）。設定していなければ null
    public record Author(String username, String name, String avatarUrl) {
    }
}
