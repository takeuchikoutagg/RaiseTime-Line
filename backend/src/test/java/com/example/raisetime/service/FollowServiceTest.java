package com.example.raisetime.service;

import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.BadRequestException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.FollowMapper;
import com.example.raisetime.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FollowService followService;

    private void userExists(String username, long id) {
        User user = new User(username, username + "さん", username + "@example.com", "hash");
        user.setId(id);
        when(userMapper.findByUsername(username)).thenReturn(user);
    }

    @Test
    void follow_insertsIgnoringDuplicates_andReturnsFollowerCount() {
        userExists("hanako", 2L);
        when(followMapper.countFollowers(2L)).thenReturn(16L);

        FollowResponse response = followService.follow("hanako", 1L);

        verify(followMapper).insertIgnore(1L, 2L);
        assertThat(response).isEqualTo(new FollowResponse(true, 16L));
    }

    @Test
    void follow_alreadyFollowing_isStillOk() {
        userExists("hanako", 2L);
        when(followMapper.insertIgnore(1L, 2L)).thenReturn(0);
        when(followMapper.countFollowers(2L)).thenReturn(1L);

        assertThat(followService.follow("hanako", 1L)).isEqualTo(new FollowResponse(true, 1L));
    }

    @Test
    void unfollow_deletes_andReturnsNotFollowing_evenIfNotFollowing() {
        userExists("hanako", 2L);
        when(followMapper.delete(1L, 2L)).thenReturn(0);
        when(followMapper.countFollowers(2L)).thenReturn(15L);

        assertThat(followService.unfollow("hanako", 1L)).isEqualTo(new FollowResponse(false, 15L));
        verify(followMapper).delete(1L, 2L);
    }

    @Test
    void followOrUnfollowSelf_isBadRequest() {
        userExists("taro_dev", 1L);

        assertThatThrownBy(() -> followService.follow("taro_dev", 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("自分自身はフォローできません");
        assertThatThrownBy(() -> followService.unfollow("taro_dev", 1L))
                .isInstanceOf(BadRequestException.class);
        verify(followMapper, never()).insertIgnore(anyLong(), anyLong());
        verify(followMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    void missingUser_isNotFound() {
        assertThatThrownBy(() -> followService.follow("nobody", 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("ユーザーが見つかりません");
        assertThatThrownBy(() -> followService.unfollow("nobody", 1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void follow_userDeletedInTheMeantime_isNotFound() {
        userExists("hanako", 2L);
        when(followMapper.insertIgnore(1L, 2L)).thenThrow(new DataIntegrityViolationException("fk_follows_followee"));

        assertThatThrownBy(() -> followService.follow("hanako", 1L))
                .isInstanceOf(NotFoundException.class);
    }
}
