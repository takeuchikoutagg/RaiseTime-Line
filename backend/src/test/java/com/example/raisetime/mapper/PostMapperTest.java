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
import static org.assertj.core.groups.Tuple.tuple;

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

    // ---------- いいね数・コメント数（F-05 / F-06） ----------

    private void like(Long postId, Long userId) {
        jdbcTemplate.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", postId, userId);
    }

    private void comment(Long postId, Long userId) {
        jdbcTemplate.update("INSERT INTO comments (post_id, user_id, body) VALUES (?, ?, 'コメント')", postId, userId);
    }

    @Test
    void timeline_includesLikeAndCommentCounts_andLikedByViewer() {
        Post liked = insertPost(taro, "いいねされた投稿");
        Post plain = insertPost(taro, "何もない投稿");
        like(liked.getId(), taro);
        like(liked.getId(), hanako);
        like(liked.getId(), stranger);
        comment(liked.getId(), hanako);
        comment(liked.getId(), hanako);

        List<PostWithAuthor> rows = postMapper.findTimeline(taro, null, 20);

        assertThat(rows).extracting(Post::getId).containsExactly(plain.getId(), liked.getId());
        assertThat(rows).extracting(PostWithAuthor::getLikeCount).containsExactly(0L, 3L);
        assertThat(rows).extracting(PostWithAuthor::getCommentCount).containsExactly(0L, 2L);
        assertThat(rows).extracting(PostWithAuthor::isLikedByMe).containsExactly(false, true);
    }

    @Test
    void findDetailById_returnsAnyonesPost_withCounts_andLikedByViewer() {
        // フォローしていない人の投稿も取れる
        Post post = insertPost(stranger, "知らない人の投稿");
        like(post.getId(), hanako);
        comment(post.getId(), taro);

        PostWithAuthor byTaro = postMapper.findDetailById(post.getId(), taro);
        assertThat(byTaro.getBody()).isEqualTo("知らない人の投稿");
        assertThat(byTaro.getAuthorUsername()).isEqualTo("pm_stranger");
        assertThat(byTaro.getAuthorName()).isEqualTo("pm_strangerさん");
        assertThat(byTaro.getLikeCount()).isEqualTo(1L);
        assertThat(byTaro.getCommentCount()).isEqualTo(1L);
        assertThat(byTaro.isLikedByMe()).isFalse();
        assertThat(postMapper.findDetailById(post.getId(), hanako).isLikedByMe()).isTrue();
        assertThat(postMapper.findDetailById(-1L, taro)).isNull();
    }

    @Test
    void delete_removesCommentsAndLikesByCascade() {
        Post post = insertPost(taro, "消す投稿");
        like(post.getId(), hanako);
        comment(post.getId(), hanako);

        postMapper.delete(post.getId());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM likes WHERE post_id = ?", Long.class,
                post.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM comments WHERE post_id = ?", Long.class,
                post.getId())).isZero();
    }

    // ---------- 投稿編集（F-10） ----------

    @Test
    void updateBody_changesBodyAndEditedAt_butNotCreatedAt() {
        Post post = insertPost(taro, "編集前");
        Post before = postMapper.findById(post.getId());

        assertThat(postMapper.updateBody(post.getId(), "編集後😀")).isEqualTo(1);

        Post after = postMapper.findByIdForUpdate(post.getId());
        assertThat(after.getBody()).isEqualTo("編集後😀");
        assertThat(after.getEditedAt()).isNotNull();
        // 投稿日時は変えない（タイムラインの並び順が変わらない）
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(postMapper.findByIdForUpdate(-1L)).isNull();
    }

    @Test
    void deleteByPostId_removesOnlyThatPostsImages_andPositionsCanBeReused() {
        Post a = insertPost(taro, "a");
        Post b = insertPost(taro, "b");
        postImageMapper.insert(new PostImage(a.getId(), "posts/a/1.png", 1));
        postImageMapper.insert(new PostImage(a.getId(), "posts/a/2.png", 2));
        postImageMapper.insert(new PostImage(b.getId(), "posts/b/1.png", 1));

        assertThat(postImageMapper.deleteByPostId(a.getId())).isEqualTo(2);
        // 消したあとは、同じキー・同じ表示順で入れ直せる（編集で表示順を振り直すとき）
        postImageMapper.insert(new PostImage(a.getId(), "posts/a/2.png", 1));

        assertThat(postImageMapper.findByPostIds(List.of(a.getId(), b.getId())))
                .extracting(PostImage::getS3Key, PostImage::getPosition)
                .containsExactly(tuple("posts/a/2.png", 1),
                        tuple("posts/b/1.png", 1));
    }
}
