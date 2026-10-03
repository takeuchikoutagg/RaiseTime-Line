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
import com.example.raisetime.storage.ImageType;
import com.example.raisetime.validation.VisibleText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// F-04 投稿作成 / F-03 タイムライン / F-05 投稿詳細 / F-11 投稿削除
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    static final int MAX_BODY_LENGTH = 280;
    static final int MAX_IMAGES = 4;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final int PAGE_SIZE = 20;

    static final String BODY_MESSAGE = "本文は1〜280文字で入力してください";
    static final String TOO_MANY_IMAGES = "画像は4枚まで添付できます";
    static final String IMAGE_TYPE_MESSAGE = "JPEG・PNG・GIF・WebP の画像を選んでください";
    static final String IMAGE_TOO_LARGE = "画像は1枚5MBまでです";
    static final String POST_NOT_FOUND = "投稿が見つかりません";
    static final String CANNOT_DELETE = "この投稿は削除できません";

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final ImageStorage imageStorage;

    public PostService(PostMapper postMapper, PostImageMapper postImageMapper, ImageStorage imageStorage) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.imageStorage = imageStorage;
    }

    private record ValidatedImage(byte[] data, ImageType type) {
    }

    // ---------- F-04 投稿作成 ----------

    @Transactional
    public PostResponse create(AuthUser author, String body, List<MultipartFile> files) {
        validateBody(body);
        List<ValidatedImage> images = validateImages(files);

        Post post = new Post(author.getId(), body);
        postMapper.insert(post);

        // 画像は S3 に置き、DB にはキーだけを保存する。
        // 途中で失敗して DB がロールバックされたら、アップロード済みの画像を S3 から消す（S3 にゴミが残らないように）
        List<String> uploadedKeys = new ArrayList<>();
        afterRollback(() -> uploadedKeys.forEach(this::deleteQuietly));

        List<PostImage> saved = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            ValidatedImage image = images.get(i);
            // キーには推測されにくい UUID を使い、元のファイル名は使わない
            String key = "posts/%d/%s.%s".formatted(post.getId(), UUID.randomUUID(), image.type().extension());
            try {
                imageStorage.put(key, image.data(), image.type().contentType());
            } catch (RuntimeException e) {
                throw new StorageException("画像のアップロードに失敗しました: " + key, e);
            }
            uploadedKeys.add(key);
            PostImage postImage = new PostImage(post.getId(), key, i + 1);
            postImageMapper.insert(postImage);
            saved.add(postImage);
        }

        // 作ったばかりなので、いいね・コメントは0件
        return toResponse(post, author.getUsername(), author.getName(), saved, 0, 0, false, author.getId());
    }

    private static void validateBody(String body) {
        // 空白・見えない文字（ゼロ幅スペースなど）だけは不可。文字数は絵文字も1文字と数える
        if (!VisibleText.hasVisibleCharacter(body) || body.codePointCount(0, body.length()) > MAX_BODY_LENGTH) {
            throw new InvalidInputException("body", BODY_MESSAGE);
        }
    }

    private static List<ValidatedImage> validateImages(List<MultipartFile> files) {
        // 画像を選ばずに送ると、空のファイルが届くことがあるので除く
        List<MultipartFile> nonEmpty = files == null ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (nonEmpty.size() > MAX_IMAGES) {
            throw new InvalidInputException("images", TOO_MANY_IMAGES);
        }
        List<ValidatedImage> images = new ArrayList<>();
        for (MultipartFile file : nonEmpty) {
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new PayloadTooLargeException(IMAGE_TOO_LARGE);
            }
            byte[] data = readBytes(file);
            // 拡張子や Content-Type ではなく、ファイルの中身で判定する（拡張子を書き換えただけのファイルを弾く）
            ImageType type = ImageType.detect(data)
                    .orElseThrow(() -> new InvalidInputException("images", IMAGE_TYPE_MESSAGE));
            images.add(new ValidatedImage(data, type));
        }
        return images;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------- F-03 タイムライン ----------

    @Transactional(readOnly = true)
    public TimelineResponse timeline(Long viewerId, Long before) {
        // 1件多く取り、次のページがあるかを判定する
        List<PostWithAuthor> rows = postMapper.findTimeline(viewerId, before, PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<PostWithAuthor> page = hasMore ? rows.subList(0, PAGE_SIZE) : rows;

        // 20件分の画像をまとめて1回で取る
        Map<Long, List<PostImage>> imagesByPost = page.isEmpty() ? Map.of()
                : postImageMapper.findByPostIds(page.stream().map(Post::getId).toList()).stream()
                        .collect(Collectors.groupingBy(PostImage::getPostId));

        List<PostResponse> items = page.stream()
                .map(post -> toResponse(post, imagesByPost.getOrDefault(post.getId(), List.of()), viewerId))
                .toList();
        return new TimelineResponse(items, hasMore ? page.getLast().getId() : null);
    }

    // 画面に表示している一番新しい投稿（after）より新しい投稿があるか。after を省くと、投稿が1件でもあれば true
    @Transactional(readOnly = true)
    public boolean hasNewPosts(Long viewerId, Long after) {
        return postMapper.existsTimelineNewerThan(viewerId, after == null ? 0L : after);
    }

    // ---------- F-05 投稿詳細 ----------

    // ログインしていれば、フォローしていない人の投稿も見られる
    @Transactional(readOnly = true)
    public PostResponse findById(Long postId, Long viewerId) {
        PostWithAuthor post = postMapper.findDetailById(postId, viewerId);
        if (post == null) {
            throw new NotFoundException(POST_NOT_FOUND);
        }
        return toResponse(post, postImageMapper.findByPostIds(List.of(postId)), viewerId);
    }

    // ---------- F-11 投稿削除 ----------

    @Transactional
    public void delete(Long postId, Long viewerId) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new NotFoundException(POST_NOT_FOUND);
        }
        // 投稿者本人だけが削除できる（画面でメニューを隠すだけでなく、API でも必ず確認する）
        if (!post.getUserId().equals(viewerId)) {
            throw new ForbiddenException(CANNOT_DELETE);
        }
        List<String> keys = postImageMapper.findByPostIds(List.of(postId)).stream()
                .map(PostImage::getS3Key)
                .toList();
        postMapper.delete(postId);

        // S3 の画像は、DB の削除が確定してから消す（先に消すと、DB の削除が失敗したときに画像だけ無くなるため）。
        // 消せなくても投稿の削除は成功扱いにし、ログに残す
        afterCommit(() -> keys.forEach(this::deleteQuietly));
    }

    // ---------- 共通 ----------

    private PostResponse toResponse(PostWithAuthor post, List<PostImage> images, Long viewerId) {
        return toResponse(post, post.getAuthorUsername(), post.getAuthorName(), images, post.getLikeCount(),
                post.getCommentCount(), post.isLikedByMe(), viewerId);
    }

    private PostResponse toResponse(Post post, String authorUsername, String authorName, List<PostImage> images,
                                    long likeCount, long commentCount, boolean likedByMe, Long viewerId) {
        List<PostResponse.Image> imageResponses = images.stream()
                .map(image -> new PostResponse.Image(image.getId(), imageStorage.publicUrl(image.getS3Key()),
                        image.getPosition()))
                .toList();
        return new PostResponse(post.getId(), post.getBody(), post.getCreatedAt(), post.getEditedAt(),
                new PostResponse.Author(authorUsername, authorName), imageResponses, likeCount, commentCount,
                likedByMe, post.getUserId().equals(viewerId));
    }

    private void deleteQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("S3 の画像を消せませんでした（あとで手で消す必要があります）: {}", key, e);
        }
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void afterRollback(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
