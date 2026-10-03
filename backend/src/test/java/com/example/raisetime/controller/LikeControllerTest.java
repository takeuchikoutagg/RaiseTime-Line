package com.example.raisetime.controller;

import com.example.raisetime.dto.LikeResponse;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AccessTokenService;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.LikeService;
import com.example.raisetime.support.WebSecurityTestConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LikeController.class)
@Import(WebSecurityTestConfig.class)
class LikeControllerTest {

    private static final AuthUser TARO = new AuthUser(1L, "taro_dev", "山田太郎", null);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @MockitoBean
    private LikeService likeService;

    @MockitoBean
    private UserMapper userMapper;

    private Cookie loggedIn;

    @BeforeEach
    void setUp() {
        loggedIn = WebSecurityTestConfig.accessTokenCookie(accessTokenService, TARO);
    }

    @Test
    void like_returnsCountAndLiked() throws Exception {
        when(likeService.like(10L, 1L)).thenReturn(new LikeResponse(13L, true));

        mockMvc.perform(put("/api/posts/10/like").with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(13))
                .andExpect(jsonPath("$.likedByMe").value(true));
    }

    @Test
    void unlike_returnsCountAndNotLiked() throws Exception {
        when(likeService.unlike(10L, 1L)).thenReturn(new LikeResponse(12L, false));

        mockMvc.perform(delete("/api/posts/10/like").with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(12))
                .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    void withoutLogin_returns401_andWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(put("/api/posts/10/like").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/posts/10/like").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/posts/10/like").cookie(loggedIn)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/10/like").cookie(loggedIn)).andExpect(status().isForbidden());
        verify(likeService, never()).like(anyLong(), anyLong());
        verify(likeService, never()).unlike(anyLong(), anyLong());
    }

    @Test
    void missingPost_returns404() throws Exception {
        when(likeService.like(10L, 1L)).thenThrow(new NotFoundException("投稿が見つかりません"));

        mockMvc.perform(put("/api/posts/10/like").with(csrf()).cookie(loggedIn))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("投稿が見つかりません"));
    }

    @Test
    void postMethod_isNotAllowed() throws Exception {
        // いいねは PUT / DELETE だけ（POST だと再送で数がずれる作りになりやすいため使わない）
        mockMvc.perform(post("/api/posts/10/like").with(csrf()).cookie(loggedIn))
                .andExpect(status().isMethodNotAllowed());
    }
}
