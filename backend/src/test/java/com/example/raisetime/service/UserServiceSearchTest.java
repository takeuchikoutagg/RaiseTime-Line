package com.example.raisetime.service;

import com.example.raisetime.dto.UserSearchResponse;
import com.example.raisetime.dto.UserSummaryResponse;
import com.example.raisetime.entity.UserSummary;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceSearchTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PostService postService;

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private UserService userService;

    private static UserSummary row(long id, String username, boolean followed) {
        UserSummary row = new UserSummary();
        row.setId(id);
        row.setUsername(username);
        row.setName(username + "さん");
        row.setFollowedByMe(followed);
        return row;
    }

    @Test
    void search_returnsUsers_withFollowedAndIsMe_andNoNextOffsetWhenNoMore() {
        UserSummary withAvatar = row(2, "taro_dev", true);
        withAvatar.setAvatarKey("avatars/2/a.png");
        when(imageStorage.publicUrl("avatars/2/a.png")).thenReturn("http://img/avatars/2/a.png");
        when(userMapper.search("taro", "taro", 1L, 21, 0)).thenReturn(List.of(withAvatar, row(1, "taro_me", false)));

        UserSearchResponse response = userService.search("taro", 0, 1L);

        assertThat(response.items()).containsExactly(
                new UserSummaryResponse("taro_dev", "taro_devさん", "http://img/avatars/2/a.png", true, false),
                new UserSummaryResponse("taro_me", "taro_meさん", null, false, true));
        assertThat(response.nextOffset()).isNull();
    }

    @Test
    void search_trimsSpaces_removesLeadingAt_andLowercases() {
        when(userMapper.search("taro", "taro", 1L, 21, 0)).thenReturn(List.of());

        userService.search("  @TARO  ", 0, 1L);

        verify(userMapper).search("taro", "taro", 1L, 21, 0);
    }

    @Test
    void search_escapesLikeSpecialCharacters() {
        when(userMapper.search(anyString(), anyString(), anyLong(), anyInt(), anyInt())).thenReturn(List.of());

        userService.search("a_b%c\\d", 0, 1L);

        verify(userMapper).search("a_b%c\\d", "a\\_b\\%c\\\\d", 1L, 21, 0);
    }

    @Test
    void search_whenMoreThan20_returns20_andNextOffset() {
        List<UserSummary> rows = LongStream.rangeClosed(1, 21).mapToObj(i -> row(i + 10, "user" + i, false)).toList();
        when(userMapper.search("user", "user", 1L, 21, 40)).thenReturn(rows);

        UserSearchResponse response = userService.search("user", 40, 1L);

        assertThat(response.items()).hasSize(20);
        assertThat(response.nextOffset()).isEqualTo(60);
    }

    @Test
    void search_emptyOrTooLongKeyword_orNegativeOffset_isRejected() {
        for (String query : new String[] {null, "", "   ", "@", " @ ", "あ".repeat(51)}) {
            assertThatThrownBy(() -> userService.search(query, 0, 1L))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessage("検索キーワードは1〜50文字で入力してください");
        }
        assertThatThrownBy(() -> userService.search("taro", -1, 1L)).isInstanceOf(InvalidInputException.class);
        verify(userMapper, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void search_50EmojiKeyword_isAccepted() {
        when(userMapper.search(anyString(), anyString(), anyLong(), anyInt(), anyInt())).thenReturn(List.of());

        userService.search("😀".repeat(50), 0, 1L);

        verify(userMapper).search(anyString(), anyString(), anyLong(), anyInt(), anyInt());
    }
}
