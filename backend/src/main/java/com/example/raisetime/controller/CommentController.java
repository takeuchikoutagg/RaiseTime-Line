package com.example.raisetime.controller;

import com.example.raisetime.config.ApiErrors;
import com.example.raisetime.dto.CommentListResponse;
import com.example.raisetime.dto.CommentResponse;
import com.example.raisetime.dto.CreateCommentRequest;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// F-05 コメント。すべてログインが必要
@Tag(name = "3. コメント（F-05）", description = "投稿へのコメント")
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "コメント一覧を取得する", description = "古い順に20件。続きは ?after={nextCursor}")
    @ApiErrors({400, 404})
    @GetMapping("/api/posts/{postId}/comments")
    public CommentListResponse list(@PathVariable Long postId, @RequestParam(required = false) Long after) {
        return commentService.list(postId, after);
    }

    @Operation(summary = "コメントする")
    @ApiErrors({400, 404})
    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable Long postId, @RequestBody CreateCommentRequest request,
                                  @AuthenticationPrincipal AuthUser me) {
        return commentService.create(postId, me, request.body());
    }
}
