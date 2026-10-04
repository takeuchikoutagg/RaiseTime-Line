package com.example.raisetime.controller;

import com.example.raisetime.config.ApiErrors;
import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.security.AuthTokenIssuer;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.FollowService;
import com.example.raisetime.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// F-08 ユーザーページ / F-07 フォロー / F-12 プロフィール編集。すべてログインが必要
@Tag(name = "5. ユーザー（F-07・F-08・F-12）", description = "ユーザーページ・フォロー・プロフィール編集")
@RestController
public class UserController {

    private final UserService userService;
    private final FollowService followService;
    private final AuthTokenIssuer authTokenIssuer;

    public UserController(UserService userService, FollowService followService, AuthTokenIssuer authTokenIssuer) {
        this.userService = userService;
        this.followService = followService;
        this.authTokenIssuer = authTokenIssuer;
    }

    @Operation(summary = "プロフィールを編集する",
            description = "multipart/form-data。name（表示名、必須）・username（@ユーザーID、必須）・bio（自己紹介文）・"
                    + "avatar（プロフィール画像、2MBまで）・removeAvatar（画像を外すか）")
    @ApiErrors({400, 409, 413, 500})
    @PatchMapping(path = "/api/users/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse updateProfile(@RequestParam("name") String name,
                                             @RequestParam("username") String username,
                                             @RequestParam(name = "bio", required = false) String bio,
                                             @RequestParam(name = "avatar", required = false) MultipartFile avatar,
                                             @RequestParam(name = "removeAvatar", defaultValue = "false")
                                             boolean removeAvatar,
                                             @AuthenticationPrincipal AuthUser me,
                                             HttpServletResponse response) {
        UserProfileResponse updated = userService.updateProfile(me.getId(), name, username, bio, avatar, removeAvatar);
        // アクセストークン（JWT）には @ユーザーID と名前が入っているので、新しい値で作り直して Cookie を差し替える
        AuthUser renamed = new AuthUser(me.getId(), updated.username(), updated.name(), null);
        authTokenIssuer.reissueAccessToken(renamed, response);
        return updated;
    }

    @Operation(summary = "ユーザーのプロフィールを取得する")
    @ApiErrors({404})
    @GetMapping("/api/users/{username}")
    public UserProfileResponse profile(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return userService.profile(username, me.getId());
    }

    @Operation(summary = "ユーザーの投稿一覧を取得する", description = "新しい順に20件。次のページは ?before={nextCursor}")
    @ApiErrors({400, 404})
    @GetMapping("/api/users/{username}/posts")
    public TimelineResponse posts(@PathVariable String username, @RequestParam(required = false) Long before,
                                  @AuthenticationPrincipal AuthUser me) {
        return userService.posts(username, me.getId(), before);
    }

    // PUT / DELETE にして、連打や通信の再送があっても結果が同じ（冪等）になるようにする
    @Operation(summary = "フォローする", description = "2回呼んでも1回分（冪等）。自分自身は 400")
    @ApiErrors({400, 404})
    @PutMapping("/api/users/{username}/follow")
    public FollowResponse follow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return followService.follow(username, me.getId());
    }

    @Operation(summary = "フォローを解除する", description = "フォローしていなくても成功する（冪等）")
    @ApiErrors({400, 404})
    @DeleteMapping("/api/users/{username}/follow")
    public FollowResponse unfollow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return followService.unfollow(username, me.getId());
    }
}
