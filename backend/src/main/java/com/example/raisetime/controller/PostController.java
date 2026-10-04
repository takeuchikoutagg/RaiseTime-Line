package com.example.raisetime.controller;

import com.example.raisetime.config.ApiErrors;
import com.example.raisetime.dto.HasNewPostsResponse;
import com.example.raisetime.dto.PostResponse;
import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// F-04 投稿作成 / F-03 タイムライン / F-05 投稿詳細 / F-10 投稿編集 / F-11 投稿削除。すべてログインが必要
@Tag(name = "2. 投稿（F-03・F-04・F-10・F-11）", description = "タイムライン・投稿の作成・詳細・編集・削除")
@RestController
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "投稿する", description = "multipart/form-data。body（本文、必須、1〜280文字）と images（画像、0〜4枚、1枚5MBまで）")
    @ApiErrors({400, 413, 500})
    @PostMapping(path = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@RequestParam("body") String body,
                               @RequestParam(name = "images", required = false) List<MultipartFile> images,
                               @AuthenticationPrincipal AuthUser me) {
        return postService.create(me, body, images);
    }

    @Operation(summary = "タイムラインを取得する", description = "自分＋フォロー中の人の投稿を新しい順に20件。次のページは ?before={nextCursor}")
    @ApiErrors({400})
    @GetMapping("/api/timeline")
    public TimelineResponse timeline(@RequestParam(required = false) Long before,
                                     @AuthenticationPrincipal AuthUser me) {
        return postService.timeline(me.getId(), before);
    }

    // 画面が30秒ごとに呼び、表示中の一番新しい投稿（after）より新しい投稿があるかを確かめる
    @Operation(summary = "新しい投稿があるか確かめる", description = "表示中の一番新しい投稿の id（after）より新しい投稿があるか。件数は返さない")
    @ApiErrors({400})
    @GetMapping("/api/timeline/has-new")
    public HasNewPostsResponse hasNew(@RequestParam(required = false) Long after,
                                      @AuthenticationPrincipal AuthUser me) {
        return new HasNewPostsResponse(postService.hasNewPosts(me.getId(), after));
    }

    // 投稿詳細。フォローしていない人の投稿も見られる
    @Operation(summary = "投稿詳細を取得する")
    @ApiErrors({400, 404})
    @GetMapping("/api/posts/{postId}")
    public PostResponse findById(@PathVariable Long postId, @AuthenticationPrincipal AuthUser me) {
        return postService.findById(postId, me.getId());
    }

    @Operation(summary = "投稿を編集する",
            description = "投稿者本人だけ（他人の投稿は 403）。multipart/form-data。"
                    + "body（本文、必須）・keepImageIds（残す画像の id、複数可）・images（追加する画像）")
    @ApiErrors({400, 404, 413, 500})
    @PatchMapping(path = "/api/posts/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostResponse update(@PathVariable Long postId,
                               @RequestParam("body") String body,
                               @RequestParam(name = "keepImageIds", required = false) List<Long> keepImageIds,
                               @RequestParam(name = "images", required = false) List<MultipartFile> images,
                               @AuthenticationPrincipal AuthUser me) {
        return postService.update(postId, me, body, keepImageIds, images);
    }

    @Operation(summary = "投稿を削除する", description = "投稿者本人だけ（他人の投稿は 403）")
    @ApiErrors({400, 404})
    @DeleteMapping("/api/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long postId, @AuthenticationPrincipal AuthUser me) {
        postService.delete(postId, me.getId());
    }
}
