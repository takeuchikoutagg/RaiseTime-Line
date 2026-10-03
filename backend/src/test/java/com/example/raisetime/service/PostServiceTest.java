package com.example.raisetime.service;

import com.example.raisetime.dto.PostResponse;
import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.entity.Post;
import com.example.raisetime.entity.PostImage;
import com.example.raisetime.entity.PostWithAuthor;
import com.example.raisetime.exception.ForbiddenException;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.exception.PayloadTooLargeException;
import com.example.raisetime.exception.StorageException;
import com.example.raisetime.mapper.PostImageMapper;
import com.example.raisetime.mapper.PostMapper;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.storage.ImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// トランザクションの完了後の処理（S3 の画像の削除）を確かめるため、
// テストの中で TransactionSynchronizationManager を有効にし、コミット・ロールバックを手で起こしている
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceTest {

    private static final AuthUser TARO = new AuthUser(1L, "taro_dev", "山田太郎", null);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostImageMapper postImageMapper;

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private PostService postService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        // insert されたら id と作成日時を入れる（DB の代わり）
        doAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(100L);
            post.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
            return null;
        }).when(postMapper).insert(any(Post.class));
        when(imageStorage.publicUrl(anyString())).thenAnswer(invocation -> "http://img/" + invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private static void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private static MultipartFile file(String name, byte[] data) {
        return new MockMultipartFile("images", name, "application/octet-stream", data);
    }

    // ---------- 投稿作成：本文 ----------

    @Test
    void create_textOnly_savesPost_andReturnsResponse() {
        PostResponse response = postService.create(TARO, "はじめての投稿", null);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getBody()).isEqualTo("はじめての投稿");
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.author()).isEqualTo(new PostResponse.Author("taro_dev", "山田太郎"));
        assertThat(response.images()).isEmpty();
        assertThat(response.isMine()).isTrue();
        assertThat(response.editedAt()).isNull();
        // 作ったばかりなので、いいね・コメントは0件
        assertThat(response.likeCount()).isZero();
        assertThat(response.commentCount()).isZero();
        assertThat(response.likedByMe()).isFalse();
        verify(imageStorage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void create_bodyBlankOrTooLong_isRejected_beforeSaving() {
        // 見えない文字（改行しないスペース・ゼロ幅スペース）だけも空白と同じく不可
        for (String body : new String[] {null, "", "   ", "\u00A0", "\u200B", " \u200B\u3000 ", "あ".repeat(281)}) {
            assertThatThrownBy(() -> postService.create(TARO, body, null))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessage("本文は1〜280文字で入力してください")
                    .extracting(e -> ((InvalidInputException) e).getField()).isEqualTo("body");
        }
        verify(postMapper, never()).insert(any());
    }

    @Test
    void create_emoji280_isAccepted() {
        postService.create(TARO, "😀".repeat(280), null);

        verify(postMapper).insert(any(Post.class));
    }

    // ---------- 投稿作成：画像 ----------

    @Test
    void create_withImages_uploadsWithUuidKeys_inOrder_andSavesPositions() {
        PostResponse response = postService.create(TARO, "画像つき",
                List.of(file("a.png", PNG), file("b.jpg", JPEG)));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypes = ArgumentCaptor.forClass(String.class);
        verify(imageStorage, times(2)).put(keys.capture(), any(), contentTypes.capture());
        assertThat(keys.getAllValues().get(0)).matches("posts/100/[0-9a-f-]{36}\\.png");
        assertThat(keys.getAllValues().get(1)).matches("posts/100/[0-9a-f-]{36}\\.jpg");
        assertThat(contentTypes.getAllValues()).containsExactly("image/png", "image/jpeg");

        ArgumentCaptor<PostImage> images = ArgumentCaptor.forClass(PostImage.class);
        verify(postImageMapper, times(2)).insert(images.capture());
        assertThat(images.getAllValues()).extracting(PostImage::getPosition).containsExactly(1, 2);
        assertThat(response.images()).extracting(PostResponse.Image::url)
                .containsExactly("http://img/" + keys.getAllValues().get(0), "http://img/" + keys.getAllValues().get(1));
    }

    @Test
    void create_emptyFilePartsAreIgnored() {
        postService.create(TARO, "画像なし", List.of(file("", new byte[0])));

        verify(imageStorage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void create_fiveImages_isRejected() {
        List<MultipartFile> five = LongStream.range(0, 5).mapToObj(i -> file(i + ".png", PNG)).toList();

        assertThatThrownBy(() -> postService.create(TARO, "多すぎ", five))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("画像は4枚まで添付できます");
        verify(postMapper, never()).insert(any());
    }

    @Test
    void create_notAnImage_isRejected_evenIfNamedJpg() {
        MultipartFile fake = new MockMultipartFile("images", "photo.jpg", "image/jpeg",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> postService.create(TARO, "偽物", List.of(fake)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("JPEG・PNG・GIF・WebP の画像を選んでください");
        verify(postMapper, never()).insert(any());
    }

    @Test
    void create_imageOver5MB_isRejected() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> postService.create(TARO, "大きすぎ", List.of(file("big.png", big))))
                .isInstanceOf(PayloadTooLargeException.class);
        verify(postMapper, never()).insert(any());
    }

    @Test
    void create_uploadFails_throwsStorageException_andDeletesAlreadyUploadedImagesOnRollback() {
        doThrow(new RuntimeException("S3 down"))
                .when(imageStorage).put(org.mockito.ArgumentMatchers.endsWith(".jpg"), any(), anyString());

        assertThatThrownBy(() -> postService.create(TARO, "失敗",
                List.of(file("a.png", PNG), file("b.jpg", JPEG))))
                .isInstanceOf(StorageException.class);
        ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
        verify(imageStorage, times(2)).put(uploaded.capture(), any(), anyString());
        verify(imageStorage, never()).delete(anyString());

        // トランザクションがロールバックされたら、1枚目（アップロード済み）を S3 から消す
        rollback();
        verify(imageStorage).delete(uploaded.getAllValues().getFirst());
        verify(imageStorage, never()).delete(uploaded.getAllValues().get(1));
    }

    @Test
    void create_success_doesNotDeleteImagesOnCommit() {
        postService.create(TARO, "成功", List.of(file("a.png", PNG)));

        commit();
        verify(imageStorage, never()).delete(anyString());
    }

    // ---------- 投稿削除 ----------

    private static Post post(long id, long userId) {
        Post post = new Post(userId, "本文");
        post.setId(id);
        return post;
    }

    private static PostImage image(long postId, String key, int position) {
        PostImage image = new PostImage(postId, key, position);
        image.setId(postId * 10 + position);
        return image;
    }

    @Test
    void delete_ownPost_deletesRow_andDeletesImagesOnlyAfterCommit() {
        when(postMapper.findById(5L)).thenReturn(post(5, 1));
        when(postImageMapper.findByPostIds(List.of(5L)))
                .thenReturn(List.of(image(5, "posts/5/a.png", 1), image(5, "posts/5/b.jpg", 2)));

        postService.delete(5L, 1L);

        verify(postMapper).delete(5L);
        // DB の削除が確定するまでは S3 の画像を消さない
        verify(imageStorage, never()).delete(anyString());
        commit();
        verify(imageStorage).delete("posts/5/a.png");
        verify(imageStorage).delete("posts/5/b.jpg");
    }

    @Test
    void delete_rolledBack_keepsImages() {
        when(postMapper.findById(5L)).thenReturn(post(5, 1));
        when(postImageMapper.findByPostIds(List.of(5L))).thenReturn(List.of(image(5, "posts/5/a.png", 1)));

        postService.delete(5L, 1L);
        rollback();

        verify(imageStorage, never()).delete(anyString());
    }

    @Test
    void delete_s3Failure_isLoggedButNotThrown() {
        when(postMapper.findById(5L)).thenReturn(post(5, 1));
        when(postImageMapper.findByPostIds(List.of(5L))).thenReturn(List.of(image(5, "posts/5/a.png", 1)));
        doThrow(new RuntimeException("S3 down")).when(imageStorage).delete("posts/5/a.png");

        postService.delete(5L, 1L);
        commit();

        verify(imageStorage).delete("posts/5/a.png");
    }

    @Test
    void delete_othersPost_isForbidden() {
        when(postMapper.findById(5L)).thenReturn(post(5, 2));

        assertThatThrownBy(() -> postService.delete(5L, 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("この投稿は削除できません");
        verify(postMapper, never()).delete(any());
    }

    @Test
    void delete_missingPost_isNotFound() {
        assertThatThrownBy(() -> postService.delete(5L, 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("投稿が見つかりません");
    }

    // ---------- タイムライン ----------

    private static PostWithAuthor row(long id, long userId, String username) {
        PostWithAuthor row = new PostWithAuthor();
        row.setId(id);
        row.setUserId(userId);
        row.setBody("投稿" + id);
        row.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        row.setAuthorUsername(username);
        row.setAuthorName(username + "さん");
        return row;
    }

    @Test
    void timeline_returnsPostsWithImages_andIsMine_withoutNextCursorWhenNoMore() {
        PostWithAuthor liked = row(9, 2, "hanako");
        liked.setLikeCount(12);
        liked.setCommentCount(3);
        liked.setLikedByMe(true);
        when(postMapper.findTimeline(1L, null, 21)).thenReturn(List.of(liked, row(8, 1, "taro_dev")));
        when(postImageMapper.findByPostIds(List.of(9L, 8L))).thenReturn(List.of(image(9, "posts/9/a.png", 1)));

        TimelineResponse response = postService.timeline(1L, null);

        assertThat(response.items()).extracting(PostResponse::id).containsExactly(9L, 8L);
        assertThat(response.items()).extracting(PostResponse::isMine).containsExactly(false, true);
        assertThat(response.items().get(0).author()).isEqualTo(new PostResponse.Author("hanako", "hanakoさん"));
        assertThat(response.items().get(0).images()).extracting(PostResponse.Image::url)
                .containsExactly("http://img/posts/9/a.png");
        assertThat(response.items().get(1).images()).isEmpty();
        assertThat(response.items()).extracting(PostResponse::likeCount).containsExactly(12L, 0L);
        assertThat(response.items()).extracting(PostResponse::commentCount).containsExactly(3L, 0L);
        assertThat(response.items()).extracting(PostResponse::likedByMe).containsExactly(true, false);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void timeline_whenMoreThan20_returns20_andNextCursorIsLastId() {
        List<PostWithAuthor> rows = LongStream.rangeClosed(1, 21).map(i -> 100 - i)
                .mapToObj(id -> row(id, 1, "taro_dev")).toList();
        when(postMapper.findTimeline(eq(1L), eq(50L), eq(21))).thenReturn(rows);

        TimelineResponse response = postService.timeline(1L, 50L);

        assertThat(response.items()).hasSize(20);
        assertThat(response.nextCursor()).isEqualTo(80L);
    }

    @Test
    void hasNewPosts_passesAfter_orZeroWhenOmitted() {
        when(postMapper.existsTimelineNewerThan(1L, 50L)).thenReturn(true);

        assertThat(postService.hasNewPosts(1L, 50L)).isTrue();
        assertThat(postService.hasNewPosts(1L, null)).isFalse();
        verify(postMapper).existsTimelineNewerThan(1L, 0L);
    }

    @Test
    void timeline_empty_doesNotQueryImages() {
        TimelineResponse response = postService.timeline(1L, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        verify(postImageMapper, never()).findByPostIds(any());
    }

    // ---------- 投稿詳細 ----------

    @Test
    void findById_returnsPostWithImagesAndCounts() {
        PostWithAuthor post = row(9, 2, "hanako");
        post.setLikeCount(5);
        post.setCommentCount(2);
        post.setLikedByMe(true);
        when(postMapper.findDetailById(9L, 1L)).thenReturn(post);
        when(postImageMapper.findByPostIds(List.of(9L))).thenReturn(List.of(image(9, "posts/9/a.png", 1)));

        PostResponse response = postService.findById(9L, 1L);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.author()).isEqualTo(new PostResponse.Author("hanako", "hanakoさん"));
        assertThat(response.images()).extracting(PostResponse.Image::url).containsExactly("http://img/posts/9/a.png");
        assertThat(response.likeCount()).isEqualTo(5L);
        assertThat(response.commentCount()).isEqualTo(2L);
        assertThat(response.likedByMe()).isTrue();
        assertThat(response.isMine()).isFalse();
    }

    @Test
    void findById_missingPost_isNotFound() {
        assertThatThrownBy(() -> postService.findById(9L, 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("投稿が見つかりません");
    }

    // ---------- ユーザーの投稿一覧 ----------

    @Test
    void userPosts_buildsPageLikeTimeline() {
        List<PostWithAuthor> rows = LongStream.rangeClosed(1, 21).map(i -> 100 - i)
                .mapToObj(id -> row(id, 2, "hanako")).toList();
        when(postMapper.findByUser(2L, 1L, null, 21)).thenReturn(rows);

        TimelineResponse response = postService.userPosts(2L, 1L, null);

        assertThat(response.items()).hasSize(20);
        assertThat(response.items()).extracting(PostResponse::isMine).containsOnly(false);
        assertThat(response.nextCursor()).isEqualTo(80L);
    }

    // ---------- 投稿編集 ----------

    private void editablePost(long postId, long ownerId, PostImage... images) {
        when(postMapper.findByIdForUpdate(postId)).thenReturn(post(postId, ownerId));
        when(postImageMapper.findByPostIds(List.of(postId))).thenReturn(List.of(images));
        PostWithAuthor edited = row(postId, ownerId, "taro_dev");
        edited.setEditedAt(Instant.parse("2026-10-02T00:00:00Z"));
        when(postMapper.findDetailById(postId, ownerId)).thenReturn(edited);
    }

    private List<PostImage> insertedImages() {
        ArgumentCaptor<PostImage> captor = ArgumentCaptor.forClass(PostImage.class);
        verify(postImageMapper, atLeast(0)).insert(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void update_bodyOnly_updatesBody_keepsImagesInOrder_andReturnsEditedPost() {
        editablePost(5, 1, image(5, "posts/5/a.png", 1), image(5, "posts/5/b.png", 2));

        PostResponse response = postService.update(5L, TARO, "編集後", List.of(51L, 52L), null);

        verify(postMapper).updateBody(5L, "編集後");
        // 画像の行は消さず、並びも変わらないので何も更新しない（画像の id はそのまま）
        verify(postImageMapper, never()).deleteByIds(any());
        verify(postImageMapper, never()).updatePosition(anyLong(), anyInt());
        assertThat(insertedImages()).isEmpty();
        assertThat(response.editedAt()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));
        commit();
        verify(imageStorage, never()).delete(anyString());
    }

    @Test
    void update_reordersKeptImages_addsNewOnes_andDeletesRemovedImagesOnlyAfterCommit() {
        editablePost(5, 1, image(5, "posts/5/a.png", 1), image(5, "posts/5/b.png", 2), image(5, "posts/5/c.png", 3));

        // b → a の順に残し、c を外して、新しい画像を1枚足す
        postService.update(5L, TARO, "編集後", List.of(52L, 51L), List.of(file("new.jpg", JPEG)));

        // 外した c の行だけ消し、残す b・a は id を変えずに表示順だけ変える（重なりのチェックは遅らせてから）
        InOrder order = inOrder(postImageMapper);
        order.verify(postImageMapper).deferPositionCheck();
        order.verify(postImageMapper).deleteByIds(List.of(53L));
        verify(postImageMapper).updatePosition(52L, 1);
        verify(postImageMapper).updatePosition(51L, 2);
        List<PostImage> inserted = insertedImages();
        assertThat(inserted).extracting(PostImage::getPosition).containsExactly(3);
        assertThat(inserted.get(0).getS3Key()).matches("posts/5/[0-9a-f-]{36}\\.jpg");
        // 外した画像は、DB の更新が確定するまで消さない
        verify(imageStorage, never()).delete(anyString());
        commit();
        verify(imageStorage).delete("posts/5/c.png");
        verify(imageStorage, never()).delete("posts/5/a.png");
    }

    @Test
    void update_rolledBack_deletesOnlyNewlyUploadedImages_andKeepsRemovedOnes() {
        editablePost(5, 1, image(5, "posts/5/a.png", 1));

        postService.update(5L, TARO, "編集後", List.of(), List.of(file("new.png", PNG)));
        ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
        verify(imageStorage).put(uploaded.capture(), any(), anyString());
        rollback();

        verify(imageStorage).delete(uploaded.getValue());
        // ロールバックしたら DB の画像はそのままなので、外すはずだった画像は消さない
        verify(imageStorage, never()).delete("posts/5/a.png");
    }

    @Test
    void update_ignoresUnknownAndDuplicateKeepIds() {
        editablePost(5, 1, image(5, "posts/5/a.png", 1));

        // 99 はほかの投稿の画像 id。重複した 51 も1回だけ
        postService.update(5L, TARO, "編集後", List.of(51L, 99L, 51L), null);

        // a は残る（消さない・表示順も 1 のまま）。ほかの投稿の画像 99 には触らない
        verify(postImageMapper, never()).deleteByIds(any());
        verify(postImageMapper, never()).updatePosition(anyLong(), anyInt());
        assertThat(insertedImages()).isEmpty();
    }

    @Test
    void update_moreThan4ImagesInTotal_isRejected_beforeSaving() {
        editablePost(5, 1, image(5, "posts/5/a.png", 1), image(5, "posts/5/b.png", 2), image(5, "posts/5/c.png", 3));

        assertThatThrownBy(() -> postService.update(5L, TARO, "編集後", List.of(51L, 52L, 53L),
                List.of(file("d.png", PNG), file("e.png", PNG))))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("画像は4枚まで添付できます");
        verify(postMapper, never()).updateBody(any(), any());
        verify(imageStorage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void update_invalidBody_isRejected_beforeSaving() {
        editablePost(5, 1);

        for (String body : new String[] {null, " ", "\u200B", "あ".repeat(281)}) {
            assertThatThrownBy(() -> postService.update(5L, TARO, body, null, null))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessage("本文は1〜280文字で入力してください");
        }
        verify(postMapper, never()).updateBody(any(), any());
    }

    @Test
    void update_othersPost_isForbidden() {
        when(postMapper.findByIdForUpdate(5L)).thenReturn(post(5, 2));

        assertThatThrownBy(() -> postService.update(5L, TARO, "乗っ取り", null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("この投稿は編集できません");
        verify(postMapper, never()).updateBody(any(), any());
    }

    @Test
    void update_missingPost_isNotFound() {
        assertThatThrownBy(() -> postService.update(5L, TARO, "編集後", null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("投稿が見つかりません");
    }
}
