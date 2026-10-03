package com.example.raisetime.controller;

import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.dto.PostResponse;
import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.dto.UserProfileResponse;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ---------- ユーザーページ ----------

    @Test
    void profile_returnsCountsAndFlags() throws Exception {
        when(userService.profile("hanako", 1L))
                .thenReturn(new UserProfileResponse("hanako", "佐藤花子", 8, 15, false, false));

        mockMvc.perform(get("/api/users/hanako").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("hanako"))
                .andExpect(jsonPath("$.name").value("佐藤花子"))
                .andExpect(jsonPath("$.followingCount").value(8))
                .andExpect(jsonPath("$.followerCount").value(15))
                .andExpect(jsonPath("$.followedByMe").value(false))
                .andExpect(jsonPath("$.isMe").value(false))
                // メールアドレスなどは返さない
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void profile_missingUser_returns404() throws Exception {
        when(userService.profile("nobody", 1L)).thenThrow(new NotFoundException("ユーザーが見つかりません"));

        mockMvc.perform(get("/api/users/nobody").cookie(loggedIn))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ユーザーが見つかりません"));
    }

    @Test
    void posts_returnsItemsAndNextCursor_andPassesBefore() throws Exception {
        PostResponse post = new PostResponse(10L, "こんにちは", Instant.parse("2026-10-01T00:00:00Z"), null,
                new PostResponse.Author("hanako", "佐藤花子"), List.of(), 2, 1, true, false);
        when(userService.posts("hanako", 1L, 50L)).thenReturn(new TimelineResponse(List.of(post), 10L));

        mockMvc.perform(get("/api/users/hanako/posts").param("before", "50").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].likeCount").value(2))
                .andExpect(jsonPath("$.nextCursor").value(10));
    }

    @Test
    void posts_nonNumericBefore_returns400() throws Exception {
        mockMvc.perform(get("/api/users/hanako/posts").param("before", "abc").cookie(loggedIn))
                .andExpect(status().isBadRequest());
    }

    // ---------- フォロー ----------

    @Test
    void follow_returnsFollowedAndCount() throws Exception {
        when(followService.follow("hanako", 1L)).thenReturn(new FollowResponse(true, 16L));

        mockMvc.perform(put("/api/users/hanako/follow").with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.followerCount").value(16));
    }

    @Test
    void unfollow_returnsNotFollowedAndCount() throws Exception {
        when(followService.unfollow("hanako", 1L)).thenReturn(new FollowResponse(false, 15L));

        mockMvc.perform(delete("/api/users/hanako/follow").with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByMe").value(false))
                .andExpect(jsonPath("$.followerCount").value(15));
    }

    @Test
    void followSelf_returns400() throws Exception {
        when(followService.follow("taro_dev", 1L)).thenThrow(new BadRequestException("自分自身はフォローできません"));

        mockMvc.perform(put("/api/users/taro_dev/follow").with(csrf()).cookie(loggedIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("自分自身はフォローできません"));
    }

    @Test
    void followMissingUser_returns404() throws Exception {
        when(followService.follow("nobody", 1L)).thenThrow(new NotFoundException("ユーザーが見つかりません"));

        mockMvc.perform(put("/api/users/nobody/follow").with(csrf()).cookie(loggedIn))
                .andExpect(status().isNotFound());
    }

    // ---------- ログイン・CSRF ----------

    @Test
    void withoutLogin_returns401_andFollowWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(get("/api/users/hanako")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/hanako/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/hanako/follow").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/hanako/follow").cookie(loggedIn)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/hanako/follow").cookie(loggedIn)).andExpect(status().isForbidden());
        verify(followService, never()).follow(anyString(), anyLong());
        verify(followService, never()).unfollow(anyString(), anyLong());
    }
}
