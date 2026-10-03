package com.example.raisetime.mapper;

import com.example.raisetime.entity.Post;
import com.example.raisetime.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// いいね（LikeMapper）の SQL を、Docker の PostgreSQL で実際に実行して確認する。テストごとにロールバックされる
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LikeMapperTest {

    @Autowired
    private LikeMapper likeMapper;

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

    @BeforeEach
    void setUp() {
        taro = insertUser("lm_taro");
        hanako = insertUser("lm_hanako");
        Post post = new Post(taro, "いいねされる投稿");
        postMapper.insert(post);
        postId = post.getId();
    }

    @Test
    void insertIgnore_twice_keepsOneLike() {
        assertThat(likeMapper.insertIgnore(postId, hanako)).isEqualTo(1);
        // 2回目は UNIQUE(user_id, post_id) にぶつかるが、エラーにせず何もしない
        assertThat(likeMapper.insertIgnore(postId, hanako)).isZero();

        assertThat(likeMapper.countByPostId(postId)).isEqualTo(1L);
        assertThat(likeMapper.exists(postId, hanako)).isTrue();
        assertThat(likeMapper.exists(postId, taro)).isFalse();
    }

    @Test
    void countByPostId_countsEachUserOnce_includingOwnLike() {
        likeMapper.insertIgnore(postId, taro);
        likeMapper.insertIgnore(postId, hanako);

        assertThat(likeMapper.countByPostId(postId)).isEqualTo(2L);
    }

    @Test
    void delete_removesOnlyThatUsersLike_andDoesNothingWhenNotLiked() {
        likeMapper.insertIgnore(postId, taro);
        likeMapper.insertIgnore(postId, hanako);

        assertThat(likeMapper.delete(postId, hanako)).isEqualTo(1);
        assertThat(likeMapper.delete(postId, hanako)).isZero();

        assertThat(likeMapper.countByPostId(postId)).isEqualTo(1L);
        assertThat(likeMapper.exists(postId, taro)).isTrue();
    }

    @Test
    void insertIgnore_missingPost_isRejectedByForeignKey() {
        assertThatThrownBy(() -> likeMapper.insertIgnore(-1L, taro))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_likes_post");
    }
}
