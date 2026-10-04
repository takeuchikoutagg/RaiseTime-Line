package com.example.raisetime.controller;

import com.example.raisetime.dto.CommentListResponse;
import com.example.raisetime.dto.CommentResponse;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AccessTokenService;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.CommentService;
import com.example.raisetime.support.WebSecurityTestConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@Import(WebSecurityTestConfig.class)
class CommentControllerTest {

    private static final AuthUser TARO = new AuthUser(1L, "taro_dev", "山田太郎", null);
    private static final CommentResponse SAMPLE = new CommentResponse(88L, "おつかれさま！",
            Instant.parse("2026-09-26T11:40:00Z"), new CommentResponse.Author("hanako", "佐藤花子", null));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private UserMapper userMapper;

    private Cookie loggedIn;

    @BeforeEach
    void setUp() {
        loggedIn = WebSecurityTestConfig.accessTokenCookie(accessTokenService, TARO);
    }

    private static String json(String body) {
        return "{\"body\":\"" + body + "\"}";
    }

    // ---------- 一覧 ----------

    @Test
    void list_returnsItemsAndNextCursor_andPassesAfter() throws Exception {
        when(commentService.list(10L, 50L)).thenReturn(new CommentListResponse(List.of(SAMPLE), 88L));

        mockMvc.perform(get("/api/posts/10/comments").param("after", "50").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(88))
                .andExpect(jsonPath("$.items[0].body").value("おつかれさま！"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-09-26T11:40:00Z"))
                .andExpect(jsonPath("$.items[0].author.username").value("hanako"))
                .andExpect(jsonPath("$.items[0].author.name").value("佐藤花子"))
                .andExpect(jsonPath("$.nextCursor").value(88));
    }

    @Test
    void list_withoutAfter_passesNull() throws Exception {
        when(commentService.list(10L, null)).thenReturn(new CommentListResponse(List.of(), null));

        mockMvc.perform(get("/api/posts/10/comments").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void list_nonNumericAfter_returns400_andMissingPost_returns404() throws Exception {
        mockMvc.perform(get("/api/posts/10/comments").param("after", "abc").cookie(loggedIn))
                .andExpect(status().isBadRequest());
        when(commentService.list(11L, null)).thenThrow(new NotFoundException("投稿が見つかりません"));
        mockMvc.perform(get("/api/posts/11/comments").cookie(loggedIn))
                .andExpect(status().isNotFound());
    }

    // ---------- 作成 ----------

    @Test
    void create_returns201_andPassesBodyAndLoginUser() throws Exception {
        when(commentService.create(eq(10L), any(AuthUser.class), eq("おつかれさま！"))).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/posts/10/comments").with(csrf()).cookie(loggedIn)
                        .contentType(MediaType.APPLICATION_JSON).content(json("おつかれさま！")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(88));

        ArgumentCaptor<AuthUser> author = ArgumentCaptor.forClass(AuthUser.class);
        verify(commentService).create(eq(10L), author.capture(), eq("おつかれさま！"));
        assertThat(author.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void create_invalidBody_returns400WithField() throws Exception {
        when(commentService.create(eq(10L), any(AuthUser.class), eq("")))
                .thenThrow(new InvalidInputException("body", "コメントは1〜280文字で入力してください"));

        mockMvc.perform(post("/api/posts/10/comments").with(csrf()).cookie(loggedIn)
                        .contentType(MediaType.APPLICATION_JSON).content(json("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("コメントは1〜280文字で入力してください"))
                .andExpect(jsonPath("$.errors[0].field").value("body"));
    }

    @Test
    void create_withoutBodyField_passesNull_andBrokenJson_returns400() throws Exception {
        mockMvc.perform(post("/api/posts/10/comments").with(csrf()).cookie(loggedIn)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        verify(commentService).create(eq(10L), any(AuthUser.class), isNull());

        mockMvc.perform(post("/api/posts/10/comments").with(csrf()).cookie(loggedIn)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingPost_returns404() throws Exception {
        when(commentService.create(eq(10L), any(AuthUser.class), eq("こんにちは")))
                .thenThrow(new NotFoundException("投稿が見つかりません"));

        mockMvc.perform(post("/api/posts/10/comments").with(csrf()).cookie(loggedIn)
                        .contentType(MediaType.APPLICATION_JSON).content(json("こんにちは")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("投稿が見つかりません"));
    }

    @Test
    void withoutLogin_returns401_andCreateWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(get("/api/posts/10/comments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/posts/10/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("x")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/posts/10/comments").cookie(loggedIn)
                        .contentType(MediaType.APPLICATION_JSON).content(json("x")))
                .andExpect(status().isForbidden());
        verify(commentService, never()).create(anyLong(), any(), any());
    }
}
