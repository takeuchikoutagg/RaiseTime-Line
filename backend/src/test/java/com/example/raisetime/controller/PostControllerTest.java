package com.example.raisetime.controller;

import com.example.raisetime.dto.PostResponse;
import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.exception.ForbiddenException;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.exception.PayloadTooLargeException;
import com.example.raisetime.exception.StorageException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AccessTokenService;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.service.PostService;
import com.example.raisetime.support.WebSecurityTestConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(WebSecurityTestConfig.class)
class PostControllerTest {

    private static final AuthUser TARO = new AuthUser(1L, "taro_dev", "山田太郎", null);
    private static final PostResponse SAMPLE = new PostResponse(10L, "こんにちは", Instant.parse("2026-10-01T00:00:00Z"),
            null, new PostResponse.Author("taro_dev", "山田太郎", null),
            List.of(new PostResponse.Image(100L, "http://img/posts/10/a.png", 1)), 3, 1, false, true);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private UserMapper userMapper;

    private Cookie loggedIn;

    @BeforeEach
    void setUp() {
        loggedIn = WebSecurityTestConfig.accessTokenCookie(accessTokenService, TARO);
    }

    // ---------- ログイン・CSRF ----------

    @Test
    void allApis_withoutLogin_return401() throws Exception {
        mockMvc.perform(get("/api/timeline")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/posts").param("body", "x").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/posts/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void createAndDelete_withoutCsrf_return403() throws Exception {
        mockMvc.perform(multipart("/api/posts").param("body", "x").cookie(loggedIn))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/1").cookie(loggedIn))
                .andExpect(status().isForbidden());
        verify(postService, never()).create(any(), anyString(), any());
        verify(postService, never()).delete(anyLong(), anyLong());
    }

    // ---------- 投稿作成 ----------

    @Test
    @SuppressWarnings("unchecked")
    void create_multipart_returns201_andPassesBodyAndImages() throws Exception {
        when(postService.create(any(AuthUser.class), eq("こんにちは"), any())).thenReturn(SAMPLE);
        MockMultipartFile image = new MockMultipartFile("images", "a.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/posts").file(image).param("body", "こんにちは").with(csrf()).cookie(loggedIn))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.body").value("こんにちは"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.editedAt").isEmpty())
                .andExpect(jsonPath("$.author.username").value("taro_dev"))
                .andExpect(jsonPath("$.images[0].url").value("http://img/posts/10/a.png"))
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.isMine").value(true))
                // インプレッション数などは返さない
                .andExpect(jsonPath("$.impressions").doesNotExist());

        ArgumentCaptor<AuthUser> author = ArgumentCaptor.forClass(AuthUser.class);
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(postService).create(author.capture(), eq("こんにちは"), files.capture());
        assertThat(author.getValue().getId()).isEqualTo(1L);
        assertThat(files.getValue()).hasSize(1);
        assertThat(files.getValue().getFirst().getOriginalFilename()).isEqualTo("a.png");
    }

    @Test
    void create_withoutImages_passesNull() throws Exception {
        when(postService.create(any(AuthUser.class), eq("文字だけ"), isNull())).thenReturn(SAMPLE);

        mockMvc.perform(multipart("/api/posts").param("body", "文字だけ").with(csrf()).cookie(loggedIn))
                .andExpect(status().isCreated());
    }

    @Test
    void create_withoutBodyParam_returns400() throws Exception {
        mockMvc.perform(multipart("/api/posts").with(csrf()).cookie(loggedIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_invalidInput_returns400WithField() throws Exception {
        when(postService.create(any(), anyString(), any()))
                .thenThrow(new InvalidInputException("body", "本文は1〜280文字で入力してください"));

        mockMvc.perform(multipart("/api/posts").param("body", " ").with(csrf()).cookie(loggedIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("本文は1〜280文字で入力してください"))
                .andExpect(jsonPath("$.errors[0].field").value("body"));
    }

    @Test
    void create_tooLargeImage_returns413() throws Exception {
        when(postService.create(any(), anyString(), any())).thenThrow(new PayloadTooLargeException("画像は1枚5MBまでです"));

        mockMvc.perform(multipart("/api/posts").param("body", "x").with(csrf()).cookie(loggedIn))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void create_storageFailure_returns500WithFriendlyMessage() throws Exception {
        when(postService.create(any(), anyString(), any()))
                .thenThrow(new StorageException("upload failed", new RuntimeException("S3 down")));

        mockMvc.perform(multipart("/api/posts").param("body", "x").with(csrf()).cookie(loggedIn))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("投稿に失敗しました。時間をおいてもう一度お試しください"));
    }

    @Test
    void create_asJson_returns415() throws Exception {
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}")
                        .with(csrf()).cookie(loggedIn))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------- タイムライン ----------

    @Test
    void timeline_returnsItemsAndNextCursor_andPassesBefore() throws Exception {
        when(postService.timeline(1L, 50L)).thenReturn(new TimelineResponse(List.of(SAMPLE), 10L));

        mockMvc.perform(get("/api/timeline").param("before", "50").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.nextCursor").value(10));
    }

    @Test
    void timeline_withoutBefore_passesNull() throws Exception {
        when(postService.timeline(1L, null)).thenReturn(new TimelineResponse(List.of(), null));

        mockMvc.perform(get("/api/timeline").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void hasNew_returnsFlag_andPassesAfter() throws Exception {
        when(postService.hasNewPosts(1L, 50L)).thenReturn(true);

        mockMvc.perform(get("/api/timeline/has-new").param("after", "50").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNew").value(true))
                // 件数は返さない
                .andExpect(jsonPath("$.count").doesNotExist());
    }

    @Test
    void hasNew_withoutLogin_returns401_andNonNumericAfter_returns400() throws Exception {
        mockMvc.perform(get("/api/timeline/has-new").param("after", "50")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/timeline/has-new").param("after", "abc").cookie(loggedIn))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_nonNumericBefore_returns400() throws Exception {
        mockMvc.perform(get("/api/timeline").param("before", "abc").cookie(loggedIn))
                .andExpect(status().isBadRequest());
    }

    // ---------- 投稿詳細 ----------

    @Test
    void findById_returnsPost() throws Exception {
        when(postService.findById(10L, 1L)).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/posts/10").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.commentCount").value(1));
    }

    // 想定外のエラー（DB の障害など）でも、例外のメッセージやスタックトレースなど内部の情報は返さない
    @Test
    void unexpectedError_returns500_withoutInternalDetails() throws Exception {
        when(postService.findById(10L, 1L))
                .thenThrow(new IllegalStateException("connection to db-internal.example:5432 refused"));

        String body = mockMvc.perform(get("/api/posts/10").cookie(loggedIn))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("予期しないエラーが発生しました"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("db-internal", "IllegalStateException", "at com.example");
    }

    @Test
    void findById_missingPost_returns404_andNonNumericId_returns400() throws Exception {
        when(postService.findById(10L, 1L)).thenThrow(new NotFoundException("投稿が見つかりません"));

        mockMvc.perform(get("/api/posts/10").cookie(loggedIn))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("投稿が見つかりません"));
        mockMvc.perform(get("/api/posts/abc").cookie(loggedIn))
                .andExpect(status().isBadRequest());
    }

    // ---------- 投稿編集 ----------

    @Test
    @SuppressWarnings("unchecked")
    void update_multipartPatch_returns200_andPassesBodyKeepIdsAndImages() throws Exception {
        when(postService.update(eq(10L), any(AuthUser.class), eq("編集後"), any(), any())).thenReturn(SAMPLE);
        MockMultipartFile image = new MockMultipartFile("images", "a.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/10").file(image)
                        .param("body", "編集後").param("keepImageIds", "100", "101")
                        .with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        ArgumentCaptor<List<Long>> keep = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(postService).update(eq(10L), any(AuthUser.class), eq("編集後"), keep.capture(), files.capture());
        assertThat(keep.getValue()).containsExactly(100L, 101L);
        assertThat(files.getValue()).hasSize(1);
    }

    @Test
    void update_withoutKeepIdsAndImages_passesNull() throws Exception {
        when(postService.update(eq(10L), any(AuthUser.class), eq("編集後"), isNull(), isNull())).thenReturn(SAMPLE);

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/10").param("body", "編集後").with(csrf()).cookie(loggedIn))
                .andExpect(status().isOk());
    }

    @Test
    void update_othersPost_returns403_andMissingPost_returns404() throws Exception {
        when(postService.update(eq(10L), any(AuthUser.class), anyString(), any(), any()))
                .thenThrow(new ForbiddenException("この投稿は編集できません"));
        when(postService.update(eq(11L), any(AuthUser.class), anyString(), any(), any()))
                .thenThrow(new NotFoundException("投稿が見つかりません"));

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/10").param("body", "x").with(csrf()).cookie(loggedIn))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("この投稿は編集できません"));
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/11").param("body", "x").with(csrf()).cookie(loggedIn))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_invalidInput_returns400_andNonNumericKeepId_returns400() throws Exception {
        when(postService.update(eq(10L), any(AuthUser.class), eq(""), any(), any()))
                .thenThrow(new InvalidInputException("body", "本文は1〜280文字で入力してください"));

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/10").param("body", "").with(csrf()).cookie(loggedIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/10").param("body", "x").param("keepImageIds", "abc")
                        .with(csrf()).cookie(loggedIn))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_withoutLogin_returns401_andWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/10").param("body", "x").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/posts/10").param("body", "x").cookie(loggedIn))
                .andExpect(status().isForbidden());
        verify(postService, never()).update(anyLong(), any(), anyString(), any(), any());
    }

    // ---------- 投稿削除 ----------

    @Test
    void delete_ownPost_returns204() throws Exception {
        mockMvc.perform(delete("/api/posts/10").with(csrf()).cookie(loggedIn))
                .andExpect(status().isNoContent());
        verify(postService).delete(10L, 1L);
    }

    @Test
    void delete_othersPost_returns403() throws Exception {
        doThrow(new ForbiddenException("この投稿は削除できません")).when(postService).delete(10L, 1L);

        mockMvc.perform(delete("/api/posts/10").with(csrf()).cookie(loggedIn))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("この投稿は削除できません"));
    }

    @Test
    void delete_missingPost_returns404() throws Exception {
        doThrow(new NotFoundException("投稿が見つかりません")).when(postService).delete(10L, 1L);

        mockMvc.perform(delete("/api/posts/10").with(csrf()).cookie(loggedIn))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
