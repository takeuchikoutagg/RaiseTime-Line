package com.example.raisetime.service;

import com.example.raisetime.dto.LikeResponse;
import com.example.raisetime.entity.Post;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.LikeMapper;
import com.example.raisetime.mapper.PostMapper;
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
class LikeServiceTest {

    @Mock
    private LikeMapper likeMapper;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private LikeService likeService;

    private void postExists(long postId) {
        when(postMapper.findById(postId)).thenReturn(new Post(2L, "投稿"));
    }

    @Test
    void like_insertsIgnoringDuplicates_andReturnsCountAndLiked() {
        postExists(10L);
        when(likeMapper.countByPostId(10L)).thenReturn(13L);

        LikeResponse response = likeService.like(10L, 1L);

        verify(likeMapper).insertIgnore(10L, 1L);
        assertThat(response).isEqualTo(new LikeResponse(13L, true));
    }

    @Test
    void like_alreadyLiked_isStillOk() {
        // 2回目（insertIgnore が0件）でもエラーにしない（冪等）
        postExists(10L);
        when(likeMapper.insertIgnore(10L, 1L)).thenReturn(0);
        when(likeMapper.countByPostId(10L)).thenReturn(1L);

        assertThat(likeService.like(10L, 1L)).isEqualTo(new LikeResponse(1L, true));
    }

    @Test
    void unlike_deletes_andReturnsCountAndNotLiked_evenIfNotLiked() {
        postExists(10L);
        when(likeMapper.delete(10L, 1L)).thenReturn(0);
        when(likeMapper.countByPostId(10L)).thenReturn(4L);

        assertThat(likeService.unlike(10L, 1L)).isEqualTo(new LikeResponse(4L, false));
        verify(likeMapper).delete(10L, 1L);
    }

    @Test
    void likeAndUnlike_missingPost_isNotFound() {
        assertThatThrownBy(() -> likeService.like(10L, 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("投稿が見つかりません");
        assertThatThrownBy(() -> likeService.unlike(10L, 1L))
                .isInstanceOf(NotFoundException.class);
        verify(likeMapper, never()).insertIgnore(anyLong(), anyLong());
        verify(likeMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    void like_postDeletedInTheMeantime_isNotFound() {
        // 存在を確かめたあとに投稿が消え、外部キー違反になった
        postExists(10L);
        when(likeMapper.insertIgnore(10L, 1L)).thenThrow(new DataIntegrityViolationException("fk_likes_post"));

        assertThatThrownBy(() -> likeService.like(10L, 1L))
                .isInstanceOf(NotFoundException.class);
    }
}
