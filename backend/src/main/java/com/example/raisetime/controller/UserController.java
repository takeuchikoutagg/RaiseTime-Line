package com.example.raisetime.controller;

import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.dto.UserSearchResponse;
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

// F-07 フォロー / F-08 ユーザーページ（ユーザー情報）/ F-09 ユーザー検索。すべてログインが必要
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
        return userService.getProfile(username, me.getId());
    }

    @PutMapping("/api/users/{username}/follow")
    public FollowResponse follow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return followService.follow(username, me.getId());
    }

    @DeleteMapping("/api/users/{username}/follow")
    public FollowResponse unfollow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return followService.unfollow(username, me.getId());
    }

    // /api/users/search にすると、@ユーザーID が「search」の人のページ（/api/users/{username}）と区別できないため、
    // /api/search/users にしている
    @GetMapping("/api/search/users")
    public UserSearchResponse search(@RequestParam(name = "q", defaultValue = "") String keyword,
                                     @RequestParam(defaultValue = "0") int offset,
                                     @AuthenticationPrincipal AuthUser me) {
        return userService.search(keyword, offset, me.getId());
    }
}
