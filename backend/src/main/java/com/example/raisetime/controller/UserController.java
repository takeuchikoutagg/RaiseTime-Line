package com.example.raisetime.controller;

import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.FollowService;
import com.example.raisetime.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// F-08 ユーザーページ / F-07 フォロー。すべてログインが必要
@RestController
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    public UserController(UserService userService, FollowService followService) {
        this.userService = userService;
        this.followService = followService;
    }

    @GetMapping("/api/users/{username}")
    public UserProfileResponse profile(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return userService.profile(username, me.getId());
    }

    // そのユーザーの投稿を新しい順に20件。次のページは ?before={nextCursor}
    @GetMapping("/api/users/{username}/posts")
    public TimelineResponse posts(@PathVariable String username, @RequestParam(required = false) Long before,
                                  @AuthenticationPrincipal AuthUser me) {
        return userService.posts(username, me.getId(), before);
    }

    // PUT / DELETE にして、連打や通信の再送があっても結果が同じ（冪等）になるようにする
    @PutMapping("/api/users/{username}/follow")
    public FollowResponse follow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return followService.follow(username, me.getId());
    }

    @DeleteMapping("/api/users/{username}/follow")
    public FollowResponse unfollow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return followService.unfollow(username, me.getId());
    }
}
