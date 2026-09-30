package com.example.raisetime.controller;

import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.dto.UserSearchResponse;
import com.example.raisetime.dto.UserSummaryResponse;
import com.example.raisetime.exception.BadRequestException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AccessTokenService;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.FollowService;
import com.example.raisetime.service.UserService;
import com.example.raisetime.support.WebSecurityTestConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(WebSecurityTestConfig.class)
class UserControllerTest {

    private static final AuthUser TARO = new AuthUser(1L, "taro_dev", "山田太郎", null);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FollowService followService;

    @MockitoBean
    private UserMapper userMapper;

    private Cookie loggedIn;

    @BeforeEach
    void setUp() {
        loggedIn = WebSecurityTestConfig.accessTokenCookie(accessTokenService, TARO);
    }

    // ---------- ログインが必要 ----------

    @Test
    void allApis_withoutLogin_return401() throws Exception {
        mockMvc.perform(get("/api/users/hanako")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/search/users").param("q", "taro")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/hanako/follow").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/hanako/follow").with(csrf())).andExpect(status().isUnauthorized());
    }

    // ---------- ユーザー情報（F-08） ----------

    @Test
    void profile_returnsCountsAndFlags_forLoggedInViewer() throws Exception {
        when(userService.getProfile("hanako", 1L))
                .thenReturn(new UserProfileResponse("hanako", "佐藤花子", 3, 5, true, false));

        mockMvc.perform(get("/api/users/hanako").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("hanako"))
                .andExpect(jsonPath("$.name").value("佐藤花子"))
                .andExpect(jsonPath("$.followingCount").value(3))
                .andExpect(jsonPath("$.followerCount").value(5))
                .andExpect(jsonPath("$.followedByMe").value(true))
                // 共通仕様どおり、プロパティ名は "me" ではなく "isMe"
                .andExpect(jsonPath("$.isMe").value(false));
    }

    @Test
    void profile_unknownUser_returns404() throws Exception {
        when(userService.getProfile("nobody", 1L)).thenThrow(new NotFoundException("ユーザーが見つかりません"));

        mockMvc.perform(get("/api/users/nobody").cookie(loggedIn))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("ユーザーが見つかりません"));
    }

    // ---------- フォロー（F-07） ----------

    @Test
    void follow_withCsrf_returnsFollowState() throws Exception {
        when(followService.follow("hanako", 1L)).thenReturn(new FollowResponse(true, 6));

        mockMvc.perform(put("/api/users/hanako/follow").with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.followerCount").value(6));
    }

    // ログイン中（access_token Cookie あり）でも、CSRF トークンのない変更系のリクエストは 403 にする。
    // Cookie はブラウザが自動で送るので、ここを省くと別サイトから勝手にフォローさせられてしまう
    @Test
    void stateChangingRequests_whileLoggedIn_withoutCsrf_return403() throws Exception {
        mockMvc.perform(put("/api/users/hanako/follow").cookie(loggedIn))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/users/hanako/follow").cookie(loggedIn))
                .andExpect(status().isForbidden());
        verify(followService, never()).follow(any(), anyLong());
        verify(followService, never()).unfollow(any(), anyLong());
    }

    @Test
    void follow_self_returns400() throws Exception {
        when(followService.follow("taro_dev", 1L)).thenThrow(new BadRequestException("自分自身はフォローできません"));

        mockMvc.perform(put("/api/users/taro_dev/follow").with(csrf()).cookie(loggedIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("自分自身はフォローできません"));
    }

    @Test
    void unfollow_withCsrf_returnsFollowState() throws Exception {
        when(followService.unfollow("hanako", 1L)).thenReturn(new FollowResponse(false, 5));

        mockMvc.perform(delete("/api/users/hanako/follow").with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByMe").value(false))
                .andExpect(jsonPath("$.followerCount").value(5));
    }

    // ---------- 検索（F-09） ----------

    @Test
    void search_returnsItemsAndNextOffset() throws Exception {
        when(userService.search("taro", 20, 1L)).thenReturn(new UserSearchResponse(
                List.of(new UserSummaryResponse("taro_dev", "山田太郎", false, true)), 40));

        mockMvc.perform(get("/api/search/users").param("q", "taro").param("offset", "20").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("taro_dev"))
                .andExpect(jsonPath("$.items[0].isMe").value(true))
                .andExpect(jsonPath("$.nextOffset").value(40));
    }

    @Test
    void search_offsetDefaultsToZero() throws Exception {
        when(userService.search("taro", 0, 1L)).thenReturn(new UserSearchResponse(List.of(), null));

        mockMvc.perform(get("/api/search/users").param("q", "taro").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextOffset").isEmpty());
    }

    @Test
    void search_invalidKeyword_returns400() throws Exception {
        when(userService.search(any(), anyInt(), anyLong()))
                .thenThrow(new BadRequestException("検索キーワードは1〜50文字で入力してください"));

        mockMvc.perform(get("/api/search/users").param("q", "").cookie(loggedIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void search_nonNumericOffset_returns400() throws Exception {
        mockMvc.perform(get("/api/search/users").param("q", "taro").param("offset", "abc").cookie(loggedIn))
                .andExpect(status().isBadRequest());
    }
}
