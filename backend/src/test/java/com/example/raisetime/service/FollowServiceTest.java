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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private FollowMapper followMapper;

    @InjectMocks
    private FollowService followService;

    private static User user(long id, String username) {
        User user = new User(username, "名前", username + "@example.com", "hash");
        user.setId(id);
        return user;
    }

    @Test
    void follow_insertsIfAbsent_andReturnsFollowerCount() {
        when(userMapper.findByUsername("hanako")).thenReturn(user(2, "hanako"));
        when(followMapper.countFollowers(2L)).thenReturn(6L);

        FollowResponse response = followService.follow("hanako", 1L);

        verify(followMapper).insertIfAbsent(1L, 2L);
        assertThat(response).isEqualTo(new FollowResponse(true, 6));
    }

    @Test
    void unfollow_deletes_andReturnsFollowerCount() {
        when(userMapper.findByUsername("hanako")).thenReturn(user(2, "hanako"));
        when(followMapper.countFollowers(2L)).thenReturn(5L);

        FollowResponse response = followService.unfollow("hanako", 1L);

        verify(followMapper).delete(1L, 2L);
        assertThat(response).isEqualTo(new FollowResponse(false, 5));
    }

    @Test
    void follow_self_isRejected() {
        when(userMapper.findByUsername("taro_dev")).thenReturn(user(1, "taro_dev"));

        assertThatThrownBy(() -> followService.follow("taro_dev", 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("自分自身はフォローできません");
        verify(followMapper, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    void follow_unknownUser_isNotFound() {
        assertThatThrownBy(() -> followService.follow("nobody", 1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> followService.unfollow("nobody", 1L)).isInstanceOf(NotFoundException.class);
    }
}
