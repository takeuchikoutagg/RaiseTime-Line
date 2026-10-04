package com.example.raisetime.controller;

import com.example.raisetime.config.ApiErrors;
import com.example.raisetime.dto.LikeResponse;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

// F-06 いいね。PUT / DELETE にして、連打や通信の再送があっても結果が同じ（冪等）になるようにする
@Tag(name = "4. いいね（F-06）", description = "投稿へのいいね・いいね解除")
@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @Operation(summary = "いいねする", description = "2回呼んでも1回分（冪等）")
    @ApiErrors({400, 404})
    @PutMapping("/api/posts/{postId}/like")
    public LikeResponse like(@PathVariable Long postId, @AuthenticationPrincipal AuthUser me) {
        return likeService.like(postId, me.getId());
    }

    @Operation(summary = "いいねを取り消す", description = "いいねしていなくても成功する（冪等）")
    @ApiErrors({400, 404})
    @DeleteMapping("/api/posts/{postId}/like")
    public LikeResponse unlike(@PathVariable Long postId, @AuthenticationPrincipal AuthUser me) {
        return likeService.unlike(postId, me.getId());
    }
}
