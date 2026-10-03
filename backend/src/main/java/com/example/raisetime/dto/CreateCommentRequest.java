package com.example.raisetime.dto;

// POST /api/posts/{postId}/comments（docs/機能定義書/F-05_コメント.md）。
// 本文の検証（1〜280文字・空白だけは不可）は、投稿と同じく CommentService で行う
public record CreateCommentRequest(String body) {
}
