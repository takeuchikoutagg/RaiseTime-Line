package com.example.raisetime.dto;

import java.util.List;

// GET /api/search/users（docs/機能定義書/F-09_ユーザー検索.md）。
// 続きは ?offset={nextOffset} で取る。nextOffset が null なら、それ以上の結果はない
public record UserSearchResponse(List<UserSummaryResponse> items, Integer nextOffset) {
}
