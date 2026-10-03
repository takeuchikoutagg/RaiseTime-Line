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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// F-04 投稿作成 / F-03 タイムライン / F-05 投稿詳細 / F-08 ユーザーの投稿一覧 / F-10 投稿編集 / F-11 投稿削除
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
    static final String CANNOT_EDIT = "この投稿は編集できません";

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
        List<ValidatedImage> images = validateImages(files, 0);

        Post post = new Post(author.getId(), body);
        postMapper.insert(post);

        List<String> keys = upload(post.getId(), images);
        for (int i = 0; i < keys.size(); i++) {
            postImageMapper.insert(new PostImage(post.getId(), keys.get(i), i + 1));
        }

        // 投稿者の名前・プロフィール画像は DB の最新の値を使うため、保存した投稿を取り直して返す（いいね・コメントは0件）
        return findById(post.getId(), author.getId());
    }

    private static void validateBody(String body) {
        // 空白・見えない文字（ゼロ幅スペースなど）だけは不可。文字数は絵文字も1文字と数える
        if (!VisibleText.hasVisibleCharacter(body) || body.codePointCount(0, body.length()) > MAX_BODY_LENGTH) {
            throw new InvalidInputException("body", BODY_MESSAGE);
        }
    }

    // 画像を S3 に置き、キーを返す（DB にはキーだけを保存する）。
    // 途中で失敗して DB がロールバックされたら、アップロード済みの画像を S3 から消す（S3 にゴミが残らないように）
    private List<String> upload(Long postId, List<ValidatedImage> images) {
        List<String> uploadedKeys = new ArrayList<>();
        AfterTransaction.afterRollback(() -> uploadedKeys.forEach(this::deleteQuietly));
        for (ValidatedImage image : images) {
            // キーには推測されにくい UUID を使い、元のファイル名は使わない
            String key = "posts/%d/%s.%s".formatted(postId, UUID.randomUUID(), image.type().extension());
            try {
                imageStorage.put(key, image.data(), image.type().contentType());
            } catch (RuntimeException e) {
                throw new StorageException("画像のアップロードに失敗しました: " + key, e);
            }
            uploadedKeys.add(key);
        }
        return uploadedKeys;
    }

    // existingCount：すでに付いていて残す画像の枚数（編集のとき）。合わせて4枚まで
    private static List<ValidatedImage> validateImages(List<MultipartFile> files, int existingCount) {
        // 画像を選ばずに送ると、空のファイルが届くことがあるので除く
        List<MultipartFile> nonEmpty = files == null ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (existingCount + nonEmpty.size() > MAX_IMAGES) {
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
        return toPage(postMapper.findTimeline(viewerId, before, PAGE_SIZE + 1), viewerId);
    }

    // ユーザーページ（F-08）の投稿一覧。並び・ページの分け方はタイムラインと同じ
    @Transactional(readOnly = true)
    public TimelineResponse userPosts(Long userId, Long viewerId, Long before) {
        return toPage(postMapper.findByUser(userId, viewerId, before, PAGE_SIZE + 1), viewerId);
    }

    // PAGE_SIZE + 1 件取った結果から、20件分のレスポンスと次のカーソルを作る
    private TimelineResponse toPage(List<PostWithAuthor> rows, Long viewerId) {
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

    // ---------- F-10 投稿編集 ----------

    // 本文と画像を変える。投稿日時（created_at）は変えないので、タイムラインの並び順も変わらない。いいね・コメントはそのまま。
    // keepImageIds：今の画像のうち残すもの（送った順に並べる）。送らなかった画像は外す。files：追加する画像
    @Transactional
    public PostResponse update(Long postId, AuthUser editor, String body, List<Long> keepImageIds,
                               List<MultipartFile> files) {
        // 同じ投稿への編集・削除が同時に走らないよう、行をロックして取る
        Post post = postMapper.findByIdForUpdate(postId);
        if (post == null) {
            throw new NotFoundException(POST_NOT_FOUND);
        }
        // 投稿者本人だけが編集できる（画面でメニューを隠すだけでなく、API でも必ず確認する）
        if (!post.getUserId().equals(editor.getId())) {
            throw new ForbiddenException(CANNOT_EDIT);
        }
        validateBody(body);

        // 残す画像：この投稿の画像だけを、送られた順に（重複や、ほかの投稿の画像 id は無視する）
        List<PostImage> current = postImageMapper.findByPostIds(List.of(postId));
        Map<Long, PostImage> currentById = current.stream()
                .collect(Collectors.toMap(PostImage::getId, image -> image));
        List<PostImage> kept = keepImageIds == null ? List.of()
                : keepImageIds.stream().distinct().map(currentById::get).filter(Objects::nonNull).toList();
        List<ValidatedImage> added = validateImages(files, kept.size());
        List<PostImage> removed = current.stream().filter(image -> !kept.contains(image)).toList();

        List<String> addedKeys = upload(postId, added);
        postMapper.updateBody(postId, body);

        // 表示順を「残す画像（送った順）→ 追加する画像」で 1〜4 に振り直す。
        // 画像の行は消さずに position だけ変えるので、画像の id は変わらない（古い画面から保存しても、残す画像を正しく指せる）。
        // 入れ替えの途中で表示順が一時的に重なってもよいよう、重なりのチェックはコミット時まで遅らせる
        postImageMapper.deferPositionCheck();
        if (!removed.isEmpty()) {
            postImageMapper.deleteByIds(removed.stream().map(PostImage::getId).toList());
        }
        int position = 1;
        for (PostImage image : kept) {
            if (image.getPosition() != position) {
                postImageMapper.updatePosition(image.getId(), position);
            }
            position++;
        }
        for (String key : addedKeys) {
            postImageMapper.insert(new PostImage(postId, key, position++));
        }
        List<String> removedKeys = removed.stream().map(PostImage::getS3Key).toList();

        // 外した画像は、DB の更新が確定してから S3 から消す。消せなくても編集は成功扱いにし、ログに残す
        AfterTransaction.afterCommit(() -> removedKeys.forEach(this::deleteQuietly));
        return findById(postId, editor.getId());
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
        AfterTransaction.afterCommit(() -> keys.forEach(this::deleteQuietly));
    }

    // ---------- 共通 ----------

    private PostResponse toResponse(PostWithAuthor post, List<PostImage> images, Long viewerId) {
        List<PostResponse.Image> imageResponses = images.stream()
                .map(image -> new PostResponse.Image(image.getId(), imageStorage.publicUrl(image.getS3Key()),
                        image.getPosition()))
                .toList();
        PostResponse.Author author = new PostResponse.Author(post.getAuthorUsername(), post.getAuthorName(),
                avatarUrl(post.getAuthorAvatarKey()));
        return new PostResponse(post.getId(), post.getBody(), post.getCreatedAt(), post.getEditedAt(), author,
                imageResponses, post.getLikeCount(), post.getCommentCount(), post.isLikedByMe(),
                post.getUserId().equals(viewerId));
    }

    // プロフィール画像のキーから URL を作る。設定していなければ null
    private String avatarUrl(String avatarKey) {
        return avatarKey == null ? null : imageStorage.publicUrl(avatarKey);
    }

    private void deleteQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("S3 の画像を消せませんでした（あとで手で消す必要があります）: {}", key, e);
        }
    }
}
