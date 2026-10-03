package com.example.raisetime.service;

import com.example.raisetime.dto.LikeResponse;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.LikeMapper;
import com.example.raisetime.mapper.PostMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// F-06 いいね。何度送っても結果が同じ（冪等）になるようにする：いいね済みでいいね → そのまま、未いいねで解除 → そのまま
@Service
public class LikeService {

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;

    public LikeService(LikeMapper likeMapper, PostMapper postMapper) {
        this.likeMapper = likeMapper;
        this.postMapper = postMapper;
    }

    @Transactional
    public LikeResponse like(Long postId, Long userId) {
        requirePost(postId);
        try {
            likeMapper.insertIgnore(postId, userId);
        } catch (DataIntegrityViolationException e) {
            // 存在を確かめたあとに投稿が削除された（外部キー違反）
            throw new NotFoundException(PostService.POST_NOT_FOUND);
        }
        return new LikeResponse(likeMapper.countByPostId(postId), true);
    }

    @Transactional
    public LikeResponse unlike(Long postId, Long userId) {
        requirePost(postId);
        likeMapper.delete(postId, userId);
        return new LikeResponse(likeMapper.countByPostId(postId), false);
    }

    private void requirePost(Long postId) {
        if (postMapper.findById(postId) == null) {
            throw new NotFoundException(PostService.POST_NOT_FOUND);
        }
    }
}
