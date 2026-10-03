package com.example.raisetime.dto;

import java.util.List;

// GET /api/posts/{postId}/comments（docs/機能定義書/F-05_コメント.md）。コメントは古い順。
// 続きは ?after={nextCursor} で取る。nextCursor が null なら、それ以上のコメントはない
public record CommentListResponse(List<CommentResponse> items, Long nextCursor) {
}
