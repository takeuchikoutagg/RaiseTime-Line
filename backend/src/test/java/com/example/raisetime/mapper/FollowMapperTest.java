package com.example.raisetime.mapper;

import com.example.raisetime.entity.Post;
import com.example.raisetime.entity.PostWithAuthor;
import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// フォロー（FollowMapper）・ユーザーページ（UserMapper.findProfile / PostMapper.findByUser）の SQL を、
// Docker の PostgreSQL で実際に実行して確認する。テストごとにロールバックされる
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FollowMapperTest {

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long taro;
    private Long hanako;
    private Long ichiro;

    private Long insertUser(String username) {
        User user = new User(username, username + "さん", username + "@example.com", "$2a$10$hash");
        userMapper.insert(user);
        return user.getId();
    }

    private Post insertPost(Long userId, String body) {
        Post post = new Post(userId, body);
        postMapper.insert(post);
        return post;
    }

    @BeforeEach
    void setUp() {
        taro = insertUser("fm_taro");
        hanako = insertUser("fm_hanako");
        ichiro = insertUser("fm_ichiro");
    }

    // ---------- フォロー ----------

    @Test
    void insertIgnore_twice_keepsOneFollow() {
        assertThat(followMapper.insertIgnore(taro, hanako)).isEqualTo(1);
        // 2回目は UNIQUE(follower_id, followee_id) にぶつかるが、エラーにせず何もしない
        assertThat(followMapper.insertIgnore(taro, hanako)).isZero();

        assertThat(followMapper.countFollowers(hanako)).isEqualTo(1L);
        assertThat(followMapper.countFollowers(taro)).isZero();
    }

    @Test
    void delete_removesOnlyThatFollow_andDoesNothingWhenNotFollowing() {
        followMapper.insertIgnore(taro, hanako);
        followMapper.insertIgnore(ichiro, hanako);

        assertThat(followMapper.delete(taro, hanako)).isEqualTo(1);
        assertThat(followMapper.delete(taro, hanako)).isZero();

        assertThat(followMapper.countFollowers(hanako)).isEqualTo(1L);
    }

    @Test
    void followSelf_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> followMapper.insertIgnore(taro, taro))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_follows_not_self");
    }

    @Test
    void followMissingUser_isRejectedByForeignKey() {
        assertThatThrownBy(() -> followMapper.insertIgnore(taro, -1L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_follows_followee");
    }

    // ---------- ユーザーページのプロフィール ----------

    @Test
    void findProfile_countsFollowingAndFollowers_andFollowedByViewer() {
        // taro → hanako、taro → ichiro、ichiro → taro
        followMapper.insertIgnore(taro, hanako);
        followMapper.insertIgnore(taro, ichiro);
        followMapper.insertIgnore(ichiro, taro);

        UserProfile byIchiro = userMapper.findProfile("fm_taro", ichiro);
        assertThat(byIchiro.getId()).isEqualTo(taro);
        assertThat(byIchiro.getUsername()).isEqualTo("fm_taro");
        assertThat(byIchiro.getName()).isEqualTo("fm_taroさん");
        assertThat(byIchiro.getFollowingCount()).isEqualTo(2L);
        assertThat(byIchiro.getFollowerCount()).isEqualTo(1L);
        assertThat(byIchiro.isFollowedByMe()).isTrue();

        assertThat(userMapper.findProfile("fm_taro", hanako).isFollowedByMe()).isFalse();
        assertThat(userMapper.findProfile("fm_hanako", taro).isFollowedByMe()).isTrue();
        assertThat(userMapper.findProfile("no_such_user", taro)).isNull();
    }

    @Test
    void findByUsername_returnsUserOrNull() {
        assertThat(userMapper.findByUsername("fm_hanako").getId()).isEqualTo(hanako);
        assertThat(userMapper.findByUsername("no_such_user")).isNull();
    }

    // ---------- ユーザーページの投稿一覧 ----------

    @Test
    void findByUser_onlyThatUsersPosts_newestFirst_withCountsForViewer() {
        Post old = insertPost(hanako, "花子1");
        insertPost(taro, "太郎の投稿");
        Post latest = insertPost(hanako, "花子2");
        jdbcTemplate.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", latest.getId(), taro);
        jdbcTemplate.update("INSERT INTO comments (post_id, user_id, body) VALUES (?, ?, 'コメント')", old.getId(), taro);

        // フォローしていなくても取れる
        List<PostWithAuthor> rows = postMapper.findByUser(hanako, taro, null, 20);

        assertThat(rows).extracting(Post::getId).containsExactly(latest.getId(), old.getId());
        assertThat(rows.get(0).getAuthorUsername()).isEqualTo("fm_hanako");
        assertThat(rows).extracting(PostWithAuthor::getLikeCount).containsExactly(1L, 0L);
        assertThat(rows).extracting(PostWithAuthor::getCommentCount).containsExactly(0L, 1L);
        assertThat(rows).extracting(PostWithAuthor::isLikedByMe).containsExactly(true, false);
    }

    @Test
    void findByUser_beforeCursor_andLimit() {
        Post p1 = insertPost(hanako, "1");
        Post p2 = insertPost(hanako, "2");
        Post p3 = insertPost(hanako, "3");

        assertThat(postMapper.findByUser(hanako, taro, null, 2)).extracting(Post::getId)
                .containsExactly(p3.getId(), p2.getId());
        assertThat(postMapper.findByUser(hanako, taro, p2.getId(), 2)).extracting(Post::getId)
                .containsExactly(p1.getId());
        assertThat(postMapper.findByUser(ichiro, taro, null, 20)).isEmpty();
    }
}
