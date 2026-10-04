package com.example.raisetime.controller;

import com.example.raisetime.config.ApiErrors;
import com.example.raisetime.dto.UserSearchResponse;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// F-09 ユーザー検索。ログインが必要。
// パスを /api/users/search にすると、ユーザー情報の /api/users/{username} とぶつかり、
// @ユーザーID が「search」の人のページが開けなくなるため、/api/search/users にしている
@Tag(name = "6. ユーザー検索（F-09）", description = "@ユーザーID・名前でユーザーを探す")
@RestController
public class SearchController {

    private final UserService userService;

    public SearchController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "ユーザーを検索する", description = "@ユーザーID か名前の部分一致で20件。続きは ?offset={nextOffset}")
    @ApiErrors({400})
    @GetMapping("/api/search/users")
    public UserSearchResponse searchUsers(@RequestParam("q") String query,
                                          @RequestParam(defaultValue = "0") int offset,
                                          @AuthenticationPrincipal AuthUser me) {
        return userService.search(query, offset, me.getId());
    }
}
