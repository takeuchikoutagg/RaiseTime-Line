package com.example.raisetime.controller;

import com.example.raisetime.dto.UserSearchResponse;
import com.example.raisetime.dto.UserSummaryResponse;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AccessTokenService;
import com.example.raisetime.security.AuthUser;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import(WebSecurityTestConfig.class)
class SearchControllerTest {

    private static final AuthUser TARO = new AuthUser(1L, "taro_dev", "山田太郎", null);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserMapper userMapper;

    private Cookie loggedIn;

    @BeforeEach
    void setUp() {
        loggedIn = WebSecurityTestConfig.accessTokenCookie(accessTokenService, TARO);
    }

    @Test
    void search_returnsItemsAndNextOffset_andPassesQueryAndOffset() throws Exception {
        when(userService.search("taro", 20, 1L)).thenReturn(new UserSearchResponse(
                List.of(new UserSummaryResponse("taro_dev", "山田太郎", null, false, true)), 40));

        mockMvc.perform(get("/api/search/users").param("q", "taro").param("offset", "20").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("taro_dev"))
                .andExpect(jsonPath("$.items[0].name").value("山田太郎"))
                .andExpect(jsonPath("$.items[0].avatarUrl").isEmpty())
                .andExpect(jsonPath("$.items[0].followedByMe").value(false))
                .andExpect(jsonPath("$.items[0].isMe").value(true))
                // メールアドレスなどは返さない
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.nextOffset").value(40));
    }

    @Test
    void search_withoutOffset_startsFromZero() throws Exception {
        when(userService.search("taro", 0, 1L)).thenReturn(new UserSearchResponse(List.of(), null));

        mockMvc.perform(get("/api/search/users").param("q", "taro").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextOffset").isEmpty());
    }

    @Test
    void search_invalidInputs_return400() throws Exception {
        when(userService.search("", 0, 1L)).thenThrow(new InvalidInputException("q", "検索キーワードは1〜50文字で入力してください"));

        mockMvc.perform(get("/api/search/users").param("q", "").cookie(loggedIn))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/search/users").cookie(loggedIn))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/search/users").param("q", "taro").param("offset", "abc").cookie(loggedIn))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/search/users").param("q", "taro"))
                .andExpect(status().isUnauthorized());
        verify(userService, never()).search(anyString(), anyInt(), anyLong());
    }
}
