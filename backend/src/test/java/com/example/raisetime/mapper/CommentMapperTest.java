package com.example.raisetime.mapper;

import com.example.raisetime.entity.Comment;
import com.example.raisetime.entity.CommentWithAuthor;
import com.example.raisetime.entity.Post;
import com.example.raisetime.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// コメント（CommentMapper）の SQL を、Docker の PostgreSQL で実際に実行して確認する。テストごとにロールバックされる
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommentMapperTest {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    private Long taro;
    private Long hanako;
    private Long postId;

    private Long insertUser(String username) {
        User user = new User(username, username + "さん", username + "@example.com", "$2a$10$hash");
        userMapper.insert(user);
        return user.getId();
    }

    private Comment insertComment(Long post, Long userId, String body) {
        Comment comment = new Comment(post, userId, body);
        commentMapper.insert(comment);
        return comment;
    }

    @BeforeEach
    void setUp() {
        taro = insertUser("cm_taro");
        hanako = insertUser("cm_hanako");
        Post post = new Post(taro, "コメントされる投稿");
        postMapper.insert(post);
        postId = post.getId();
    }

    @Test
    void insert_setsIdAndCreatedAt() {
        Comment comment = insertComment(postId, hanako, "おつかれさま！");

        assertThat(comment.getId()).isNotNull();
        assertThat(comment.getCreatedAt()).isNotNull();
    }

    @Test
    void findByPostId_oldestFirst_withAuthor_onlyThatPost() {
        Post other = new Post(taro, "別の投稿");
        postMapper.insert(other);
        Comment first = insertComment(postId, hanako, "1つ目");
        insertComment(other.getId(), hanako, "別の投稿へのコメント");
        Comment second = insertComment(postId, taro, "2つ目");

        List<CommentWithAuthor> rows = commentMapper.findByPostId(postId, null, 20);

        assertThat(rows).extracting(Comment::getId).containsExactly(first.getId(), second.getId());
        assertThat(rows.get(0).getBody()).isEqualTo("1つ目");
        assertThat(rows.get(0).getAuthorUsername()).isEqualTo("cm_hanako");
        assertThat(rows.get(0).getAuthorName()).isEqualTo("cm_hanakoさん");
        assertThat(rows.get(1).getAuthorUsername()).isEqualTo("cm_taro");
    }

    @Test
    void findByPostId_afterCursor_andLimit() {
        Comment c1 = insertComment(postId, hanako, "c1");
        Comment c2 = insertComment(postId, hanako, "c2");
        Comment c3 = insertComment(postId, hanako, "c3");
        Comment c4 = insertComment(postId, hanako, "c4");

        assertThat(commentMapper.findByPostId(postId, null, 2)).extracting(Comment::getId)
                .containsExactly(c1.getId(), c2.getId());
        assertThat(commentMapper.findByPostId(postId, c2.getId(), 2)).extracting(Comment::getId)
                .containsExactly(c3.getId(), c4.getId());
        assertThat(commentMapper.findByPostId(postId, c4.getId(), 2)).isEmpty();
    }

    @Test
    void bodyLengthCheck_countsCharactersNotBytes() {
        // 絵文字280文字は OK、281文字は DB の CHECK 制約で止まる
        assertThat(insertComment(postId, hanako, "😀".repeat(280)).getId()).isNotNull();
        assertThatThrownBy(() -> insertComment(postId, hanako, "😀".repeat(281)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_comments_body_length");
    }

    @Test
    void insert_missingPost_isRejectedByForeignKey() {
        assertThatThrownBy(() -> insertComment(-1L, hanako, "存在しない投稿へ"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_comments_post");
    }
}
