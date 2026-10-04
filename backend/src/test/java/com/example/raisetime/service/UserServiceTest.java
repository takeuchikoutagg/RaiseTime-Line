package com.example.raisetime.service;

import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PostService postService;

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private UserService userService;

    private static UserProfile profile(long id) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUsername("hanako");
        profile.setName("佐藤花子");
        profile.setBio("");
        profile.setFollowingCount(8);
        profile.setFollowerCount(15);
        profile.setFollowedByMe(true);
        return profile;
    }

    @Test
    void profile_ofOtherUser_isNotMe() {
        when(userMapper.findProfile("hanako", 1L)).thenReturn(profile(2L));

        assertThat(userService.profile("hanako", 1L))
                .isEqualTo(new UserProfileResponse("hanako", "佐藤花子", "", null, 8, 15, true, false));
    }

    @Test
    void profile_ofMyself_isMe() {
        when(userMapper.findProfile("hanako", 2L)).thenReturn(profile(2L));

        assertThat(userService.profile("hanako", 2L).isMe()).isTrue();
    }

    @Test
    void profile_missingUser_isNotFound() {
        assertThatThrownBy(() -> userService.profile("nobody", 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("ユーザーが見つかりません");
    }

    @Test
    void posts_passesUserIdViewerAndCursor() {
        User hanako = new User("hanako", "佐藤花子", "hanako@example.com", "hash");
        hanako.setId(2L);
        when(userMapper.findByUsername("hanako")).thenReturn(hanako);
        TimelineResponse page = new TimelineResponse(List.of(), null);
        when(postService.userPosts(2L, 1L, 50L)).thenReturn(page);

        assertThat(userService.posts("hanako", 1L, 50L)).isSameAs(page);
    }

    @Test
    void posts_missingUser_isNotFound() {
        assertThatThrownBy(() -> userService.posts("nobody", 1L, null))
                .isInstanceOf(NotFoundException.class);
        verify(postService, never()).userPosts(anyLong(), anyLong(), any());
    }

    @Test
    void uppercaseUsernameInUrl_isLookedUpAsLowercase() {
        when(userMapper.findProfile("hanako", 1L)).thenReturn(profile(2L));
        User hanako = new User("hanako", "佐藤花子", "hanako@example.com", "hash");
        hanako.setId(2L);
        when(userMapper.findByUsername("hanako")).thenReturn(hanako);

        assertThat(userService.profile("Hanako", 1L).username()).isEqualTo("hanako");
        userService.posts("HANAKO", 1L, null);
        verify(postService).userPosts(2L, 1L, null);
    }
}
