package com.example.raisetime.service;

import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.BadRequestException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.FollowMapper;
import com.example.raisetime.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// F-07 フォロー。何度呼んでも結果が同じになる（冪等）ようにしている
@Service
public class FollowService {

    static final String CANNOT_FOLLOW_SELF = "自分自身はフォローできません";

    private final UserMapper userMapper;
    private final FollowMapper followMapper;

    public FollowService(UserMapper userMapper, FollowMapper followMapper) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
    }

    // すでにフォローしていても成功にする
    @Transactional
    public FollowResponse follow(String username, Long viewerId) {
        User target = findOtherUser(username, viewerId);
        followMapper.insertIfAbsent(viewerId, target.getId());
        return new FollowResponse(true, followMapper.countFollowers(target.getId()));
    }

    // フォローしていなくても成功にする
    @Transactional
    public FollowResponse unfollow(String username, Long viewerId) {
        User target = findOtherUser(username, viewerId);
        followMapper.delete(viewerId, target.getId());
        return new FollowResponse(false, followMapper.countFollowers(target.getId()));
    }

    private User findOtherUser(String username, Long viewerId) {
        User target = userMapper.findByUsername(username);
        if (target == null) {
            throw new NotFoundException(UserService.USER_NOT_FOUND);
        }
        if (target.getId().equals(viewerId)) {
            throw new BadRequestException(CANNOT_FOLLOW_SELF);
        }
        return target;
    }
}
