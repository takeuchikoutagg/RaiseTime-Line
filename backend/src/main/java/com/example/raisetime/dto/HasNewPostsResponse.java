package com.example.raisetime.dto;

// GET /api/timeline/has-new（docs/機能定義書/F-03_タイムライン.md）。件数は返さない（画面に件数は出さないため）
public record HasNewPostsResponse(boolean hasNew) {
}
