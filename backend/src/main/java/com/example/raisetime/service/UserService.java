package com.example.raisetime.service;

import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// F-08 ユーザーページ。フォローしていない人のページも見られる
@Service
public class UserService {

    static final String USER_NOT_FOUND = "ユーザーが見つかりません";

    private final UserMapper userMapper;
    private final PostService postService;

    public UserService(UserMapper userMapper, PostService postService) {
        this.userMapper = userMapper;
        this.postService = postService;
    }

    // @ユーザーID は小文字だけ（F-01）。URL を大文字で打っても見つかるよう、小文字にそろえてから探す
    static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(String username, Long viewerId) {
        UserProfile profile = userMapper.findProfile(normalize(username), viewerId);
        if (profile == null) {
            throw new NotFoundException(USER_NOT_FOUND);
        }
        return new UserProfileResponse(profile.getUsername(), profile.getName(), profile.getFollowingCount(),
                profile.getFollowerCount(), profile.isFollowedByMe(), profile.getId().equals(viewerId));
    }

    // そのユーザーの投稿を新しい順に20件。続きは before（前回の最後の投稿の id）より古いもの
    @Transactional(readOnly = true)
    public TimelineResponse posts(String username, Long viewerId, Long before) {
        return postService.userPosts(requireUser(username).getId(), viewerId, before);
    }

    User requireUser(String username) {
        User user = userMapper.findByUsername(normalize(username));
        if (user == null) {
            throw new NotFoundException(USER_NOT_FOUND);
        }
        return user;
    }
}
