package com.example.raisetime.service;

import com.example.raisetime.dto.FollowResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.BadRequestException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.FollowMapper;
import com.example.raisetime.mapper.UserMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// F-07 フォロー。何度送っても結果が同じ（冪等）：フォロー中にフォロー → そのまま、未フォローで解除 → そのまま。
// 相手の承認はいらない。自分自身はフォローできない（API で拒否し、DB の CHECK 制約でも防ぐ）
@Service
public class FollowService {

    static final String CANNOT_FOLLOW_SELF = "自分自身はフォローできません";

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    public FollowService(FollowMapper followMapper, UserMapper userMapper) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public FollowResponse follow(String username, Long viewerId) {
        User target = requireOtherUser(username, viewerId);
        try {
            followMapper.insertIgnore(viewerId, target.getId());
        } catch (DataIntegrityViolationException e) {
            // 確かめたあとに相手のユーザーが削除された（外部キー違反）
            throw new NotFoundException(UserService.USER_NOT_FOUND);
        }
        return new FollowResponse(true, followMapper.countFollowers(target.getId()));
    }

    @Transactional
    public FollowResponse unfollow(String username, Long viewerId) {
        User target = requireOtherUser(username, viewerId);
        followMapper.delete(viewerId, target.getId());
        return new FollowResponse(false, followMapper.countFollowers(target.getId()));
    }

    private User requireOtherUser(String username, Long viewerId) {
        User target = userMapper.findByUsername(UserService.normalize(username));
        if (target == null) {
            throw new NotFoundException(UserService.USER_NOT_FOUND);
        }
        if (target.getId().equals(viewerId)) {
            throw new BadRequestException(CANNOT_FOLLOW_SELF);
        }
        return target;
    }
}
