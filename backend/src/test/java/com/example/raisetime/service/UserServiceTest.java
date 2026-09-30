package com.example.raisetime.service;

import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.dto.UserSearchResponse;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.entity.UserSummary;
import com.example.raisetime.exception.BadRequestException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private static UserSummary summary(long id, String username) {
        UserSummary user = new UserSummary();
        user.setId(id);
        user.setUsername(username);
        user.setName("名前" + id);
        return user;
    }

    // ---------- プロフィール ----------

    @Test
    void getProfile_ofOtherUser_isMeFalse() {
        UserProfile profile = new UserProfile();
        profile.setId(2L);
        profile.setUsername("hanako");
        profile.setName("佐藤花子");
        profile.setFollowerCount(5);
        profile.setFollowedByMe(true);
        when(userMapper.findProfile("hanako", 1L)).thenReturn(profile);

        UserProfileResponse response = userService.getProfile("hanako", 1L);

        assertThat(response.isMe()).isFalse();
        assertThat(response.followedByMe()).isTrue();
        assertThat(response.followerCount()).isEqualTo(5);
    }

    @Test
    void getProfile_ofSelf_isMeTrue() {
        UserProfile profile = new UserProfile();
        profile.setId(1L);
        profile.setUsername("taro_dev");
        when(userMapper.findProfile("taro_dev", 1L)).thenReturn(profile);

        assertThat(userService.getProfile("taro_dev", 1L).isMe()).isTrue();
    }

    @Test
    void getProfile_unknown_throwsNotFound() {
        assertThatThrownBy(() -> userService.getProfile("nobody", 1L)).isInstanceOf(NotFoundException.class);
    }

    // ---------- 検索キーワードの扱い ----------

    @Test
    void normalizeKeyword_stripsSpacesAndLeadingAt_andLowercases() {
        assertThat(UserService.normalizeKeyword("  @Taro_Dev ")).isEqualTo("taro_dev");
        assertThat(UserService.normalizeKeyword("@ 花子")).isEqualTo("花子");
    }

    @Test
    void normalizeKeyword_emptyOrOnlyAt_orTooLong_isRejected() {
        assertThatThrownBy(() -> UserService.normalizeKeyword("")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> UserService.normalizeKeyword("  ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> UserService.normalizeKeyword("@")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> UserService.normalizeKeyword(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> UserService.normalizeKeyword("a".repeat(51))).isInstanceOf(BadRequestException.class);
        // 絵文字も1文字と数える
        assertThat(UserService.normalizeKeyword("😀".repeat(50))).isEqualTo("😀".repeat(50));
    }

    @Test
    void escapeLike_escapesBackslashPercentAndUnderscore() {
        assertThat(UserService.escapeLike("a_b%c\\d")).isEqualTo("a\\_b\\%c\\\\d");
    }

    // ---------- 検索のページング ----------

    @Test
    void search_passesEscapedKeyword_andAsksForOneExtraRow() {
        when(userMapper.search("taro\\_", 1L, 21, 0)).thenReturn(List.of(summary(1, "taro_dev")));

        UserSearchResponse response = userService.search("@Taro_", 0, 1L);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().isMe()).isTrue();
        assertThat(response.nextOffset()).isNull();
    }

    @Test
    void search_whenMoreThanPageSize_returns20AndNextOffset() {
        List<UserSummary> rows = IntStream.rangeClosed(1, 21).mapToObj(i -> summary(i + 10, "user" + i)).toList();
        when(userMapper.search(eq("user"), eq(1L), eq(21), eq(40))).thenReturn(rows);

        UserSearchResponse response = userService.search("user", 40, 1L);

        assertThat(response.items()).hasSize(20);
        assertThat(response.nextOffset()).isEqualTo(60);
    }

    @Test
    void search_negativeOffset_isRejected() {
        assertThatThrownBy(() -> userService.search("taro", -1, 1L)).isInstanceOf(BadRequestException.class);
        verify(userMapper, never()).search(anyString(), anyLong(), anyInt(), anyInt());
    }
}
