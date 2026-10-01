package com.example.raisetime.mapper;

import com.example.raisetime.entity.Post;
import com.example.raisetime.entity.PostImage;
import com.example.raisetime.entity.PostWithAuthor;
import com.example.raisetime.entity.User;
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

// 投稿（PostMapper）と投稿画像（PostImageMapper）の SQL を、Docker の PostgreSQL で実際に実行して確認する。
// テストごとにロールバックされる
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostMapperTest {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostImageMapper postImageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long taro;
    private Long hanako;
    private Long stranger;

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

    // フォローの API はまだ無いので、テストでは SQL で直接入れる
    private void follow(Long follower, Long followee) {
        jdbcTemplate.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", follower, followee);
    }

    @BeforeEach
    void createUsers() {
        taro = insertUser("pm_taro");
        hanako = insertUser("pm_hanako");
        stranger = insertUser("pm_stranger");
    }

    @Test
    void insert_setsIdAndCreatedAt_andFindByIdReturnsPost() {
        Post post = insertPost(taro, "はじめての投稿😀");

        assertThat(post.getId()).isNotNull();
        assertThat(post.getCreatedAt()).isNotNull();
        Post found = postMapper.findById(post.getId());
        assertThat(found.getUserId()).isEqualTo(taro);
        assertThat(found.getBody()).isEqualTo("はじめての投稿😀");
        assertThat(found.getEditedAt()).isNull();
        assertThat(postMapper.findById(-1L)).isNull();
    }

    @Test
    void bodyLengthCheck_countsCharactersNotBytes() {
        // 絵文字280文字は OK、281文字は DB の CHECK 制約で止まる
        assertThat(insertPost(taro, "😀".repeat(280)).getId()).isNotNull();
        assertThatThrownBy(() -> insertPost(taro, "😀".repeat(281)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_posts_body_length");
    }

    @Test
    void timeline_containsOwnAndFolloweesPosts_newestFirst_withAuthor() {
        Post own1 = insertPost(taro, "自分1");
        Post hanakoPost = insertPost(hanako, "花子");
        insertPost(stranger, "知らない人");
        Post own2 = insertPost(taro, "自分2");
        follow(taro, hanako);

        List<PostWithAuthor> timeline = postMapper.findTimeline(taro, null, 20);

        assertThat(timeline).extracting(Post::getId).containsExactly(own2.getId(), hanakoPost.getId(), own1.getId());
        assertThat(timeline.get(1).getAuthorUsername()).isEqualTo("pm_hanako");
        assertThat(timeline.get(1).getAuthorName()).isEqualTo("pm_hanakoさん");
    }

    @Test
    void timeline_withoutFollows_isOnlyOwnPosts() {
        Post own = insertPost(taro, "自分");
        insertPost(hanako, "花子");

        assertThat(postMapper.findTimeline(taro, null, 20)).extracting(Post::getId).containsExactly(own.getId());
    }

    @Test
    void timeline_beforeCursor_andLimit() {
        Post p1 = insertPost(taro, "1");
        Post p2 = insertPost(taro, "2");
        Post p3 = insertPost(taro, "3");

        assertThat(postMapper.findTimeline(taro, null, 2)).extracting(Post::getId)
                .containsExactly(p3.getId(), p2.getId());
        assertThat(postMapper.findTimeline(taro, p2.getId(), 2)).extracting(Post::getId)
                .containsExactly(p1.getId());
    }

    @Test
    void existsTimelineNewerThan_onlyCountsOwnAndFolloweesPostsNewerThanAfter() {
        Post own = insertPost(taro, "自分");
        follow(taro, hanako);

        assertThat(postMapper.existsTimelineNewerThan(taro, own.getId())).isFalse();
        // フォローしていない人の新しい投稿は数えない
        insertPost(stranger, "知らない人");
        assertThat(postMapper.existsTimelineNewerThan(taro, own.getId())).isFalse();
        // フォロー中の人の新しい投稿は数える
        insertPost(hanako, "花子");
        assertThat(postMapper.existsTimelineNewerThan(taro, own.getId())).isTrue();
        // after=0 なら、タイムラインに投稿が1件でもあれば true
        assertThat(postMapper.existsTimelineNewerThan(taro, 0L)).isTrue();
        assertThat(postMapper.existsTimelineNewerThan(stranger, Long.MAX_VALUE)).isFalse();
    }

    @Test
    void images_findByPostIds_orderedByPostAndPosition() {
        Post a = insertPost(taro, "a");
        Post b = insertPost(taro, "b");
        postImageMapper.insert(new PostImage(a.getId(), "posts/a/2.png", 2));
        postImageMapper.insert(new PostImage(a.getId(), "posts/a/1.png", 1));
        postImageMapper.insert(new PostImage(b.getId(), "posts/b/1.png", 1));

        List<PostImage> images = postImageMapper.findByPostIds(List.of(a.getId(), b.getId()));

        assertThat(images).extracting(PostImage::getS3Key)
                .containsExactly("posts/a/1.png", "posts/a/2.png", "posts/b/1.png");
        assertThat(images.getFirst().getId()).isNotNull();
    }

    // PostgreSQL は、トランザクションの中で1回エラーになると以降の SQL がすべて失敗するため、制約ごとにテストを分ける
    @Test
    void images_samePositionTwice_isRejected() {
        Post a = insertPost(taro, "a");
        postImageMapper.insert(new PostImage(a.getId(), "posts/a/1.png", 1));

        assertThatThrownBy(() -> postImageMapper.insert(new PostImage(a.getId(), "posts/a/x.png", 1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_post_images_post_position");
    }

    @Test
    void images_positionOver4_isRejected() {
        Post a = insertPost(taro, "a");

        assertThatThrownBy(() -> postImageMapper.insert(new PostImage(a.getId(), "posts/a/5.png", 5)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_post_images_position");
    }

    @Test
    void delete_removesPost_andItsImagesByCascade() {
        Post a = insertPost(taro, "a");
        Post other = insertPost(taro, "other");
        postImageMapper.insert(new PostImage(a.getId(), "posts/a/1.png", 1));
        postImageMapper.insert(new PostImage(other.getId(), "posts/other/1.png", 1));

        assertThat(postMapper.delete(a.getId())).isEqualTo(1);

        assertThat(postMapper.findById(a.getId())).isNull();
        assertThat(postImageMapper.findByPostIds(List.of(a.getId(), other.getId())))
                .extracting(PostImage::getS3Key).containsExactly("posts/other/1.png");
        assertThat(postMapper.delete(a.getId())).isZero();
    }
}
