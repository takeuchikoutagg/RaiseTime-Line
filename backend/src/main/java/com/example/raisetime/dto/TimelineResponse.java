package com.example.raisetime.dto;

import java.util.List;

// GET /api/timeline（docs/機能定義書/F-03_タイムライン.md）
// 次のページは ?before={nextCursor} で取る。nextCursor が null なら、それ以上の投稿はない
public record TimelineResponse(List<PostResponse> items, Long nextCursor) {
}
