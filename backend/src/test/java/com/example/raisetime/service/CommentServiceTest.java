package com.example.raisetime.service;

import com.example.raisetime.dto.CommentListResponse;
import com.example.raisetime.dto.CommentResponse;
import com.example.raisetime.entity.Comment;
import com.example.raisetime.entity.CommentWithAuthor;
import com.example.raisetime.entity.Post;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.CommentMapper;
import com.example.raisetime.mapper.PostMapper;
import com.example.raisetime.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final AuthUser TARO = new AuthUser(1L, "taro_dev", "山田太郎", null);
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private CommentService commentService;

    private void postExists(long postId) {
        when(postMapper.findById(postId)).thenReturn(new Post(2L, "投稿"));
    }

    private static CommentWithAuthor row(long id) {
        CommentWithAuthor row = new CommentWithAuthor();
        row.setId(id);
        row.setPostId(10L);
        row.setUserId(2L);
        row.setBody("コメント" + id);
        row.setCreatedAt(NOW);
        row.setAuthorUsername("hanako");
        row.setAuthorName("佐藤花子");
        return row;
    }

    // ---------- 一覧 ----------

    @Test
    void list_returnsCommentsWithAuthor_withoutNextCursorWhenNoMore() {
        postExists(10L);
        when(commentMapper.findByPostId(10L, null, 21)).thenReturn(List.of(row(1), row(2)));

        CommentListResponse response = commentService.list(10L, null);

        assertThat(response.items()).extracting(CommentResponse::id).containsExactly(1L, 2L);
        assertThat(response.items().get(0)).isEqualTo(
                new CommentResponse(1L, "コメント1", NOW, new CommentResponse.Author("hanako", "佐藤花子")));
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void list_whenMoreThan20_returns20_andNextCursorIsLastId() {
        postExists(10L);
        List<CommentWithAuthor> rows = LongStream.rangeClosed(51, 71).mapToObj(CommentServiceTest::row).toList();
        when(commentMapper.findByPostId(10L, 50L, 21)).thenReturn(rows);

        CommentListResponse response = commentService.list(10L, 50L);

        assertThat(response.items()).hasSize(20);
        assertThat(response.nextCursor()).isEqualTo(70L);
    }

    @Test
    void list_missingPost_isNotFound() {
        assertThatThrownBy(() -> commentService.list(10L, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("投稿が見つかりません");
    }

    // ---------- 作成 ----------

    @Test
    void create_savesComment_andReturnsItWithAuthor() {
        postExists(10L);
        doAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(88L);
            comment.setCreatedAt(NOW);
            return null;
        }).when(commentMapper).insert(any(Comment.class));

        CommentResponse response = commentService.create(10L, TARO, "おつかれさま！😀");

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(10L);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(response).isEqualTo(
                new CommentResponse(88L, "おつかれさま！😀", NOW, new CommentResponse.Author("taro_dev", "山田太郎")));
    }

    @Test
    void create_bodyBlankOrTooLong_isRejected_beforeSaving() {
        for (String body : new String[] {null, "", "   ", "あ".repeat(281)}) {
            assertThatThrownBy(() -> commentService.create(10L, TARO, body))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessage("コメントは1〜280文字で入力してください");
        }
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void create_emoji280_isAccepted() {
        postExists(10L);

        commentService.create(10L, TARO, "😀".repeat(280));

        verify(commentMapper).insert(any(Comment.class));
    }

    @Test
    void create_missingPost_isNotFound() {
        assertThatThrownBy(() -> commentService.create(10L, TARO, "こんにちは"))
                .isInstanceOf(NotFoundException.class);
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void create_postDeletedInTheMeantime_isNotFound() {
        postExists(10L);
        doThrow(new DataIntegrityViolationException("fk_comments_post")).when(commentMapper).insert(any());

        assertThatThrownBy(() -> commentService.create(10L, TARO, "こんにちは"))
                .isInstanceOf(NotFoundException.class);
    }
}
