package com.example.raisetime.service;

import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.dto.TimelineResponse;
import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.exception.PayloadTooLargeException;
import com.example.raisetime.exception.StorageException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.storage.ImageStorage;
import com.example.raisetime.storage.ImageType;
import com.example.raisetime.validation.LineBreaks;
import com.example.raisetime.validation.VisibleText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

// F-08 ユーザーページ / F-12 プロフィール編集。フォローしていない人のページも見られる
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    static final String USER_NOT_FOUND = "ユーザーが見つかりません";
    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_BIO_LENGTH = 160;
    static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{3,15}$");
    // 新規登録（SignupRequest）・ユーザー名の重複（AuthService）と同じメッセージにする
    static final String NAME_MESSAGE = "名前は1〜50文字で入力してください";
    static final String USERNAME_MESSAGE = "@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください";
    static final String USERNAME_TAKEN = "この@ユーザーIDはすでに使われています";
    static final String BIO_MESSAGE = "自己紹介は160文字以内で入力してください";
    // プロフィール画像の上限。アイコンは小さく表示するだけなので、画面では 400×400px に縮めてから送る（数十〜100KB 程度）。
    // この上限は、画面を通さずに API を直接呼ばれたときの歯止め
    static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    static final String AVATAR_TOO_LARGE = "プロフィール画像は2MBまでです";

    private final UserMapper userMapper;
    private final PostService postService;
    private final ImageStorage imageStorage;

    public UserService(UserMapper userMapper, PostService postService, ImageStorage imageStorage) {
        this.userMapper = userMapper;
        this.postService = postService;
        this.imageStorage = imageStorage;
    }

    // @ユーザーID は小文字だけ（F-01）。URL を大文字で打っても見つかるよう、小文字にそろえてから探す
    static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(String username, Long viewerId) {
        UserProfile profile = userMapper.findProfile(normalize(username), viewerId);
        if (profile == null) {
            throw new NotFoundException(USER_NOT_FOUND);
        }
        return toResponse(profile, viewerId);
    }

    // そのユーザーの投稿を新しい順に20件。続きは before（前回の最後の投稿の id）より古いもの
    @Transactional(readOnly = true)
    public TimelineResponse posts(String username, Long viewerId, Long before) {
        return postService.userPosts(requireUser(username).getId(), viewerId, before);
    }

    User requireUser(String username) {
        User user = userMapper.findByUsername(normalize(username));
        if (user == null) {
            throw new NotFoundException(USER_NOT_FOUND);
        }
        return user;
    }

    // ---------- F-12 プロフィール編集 ----------

    // 表示名・@ユーザーID・自己紹介文・プロフィール画像を変える。
    // avatar：新しいプロフィール画像（変えないなら null）。removeAvatar：true ならプロフィール画像を外す
    @Transactional
    public UserProfileResponse updateProfile(Long userId, String name, String username, String bio,
                                             MultipartFile avatar, boolean removeAvatar) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new NotFoundException(USER_NOT_FOUND);
        }
        // multipart で届く改行（CRLF）を LF にそろえてから、チェック・保存する
        String newName = name == null ? null : LineBreaks.normalize(name).strip();
        if (newName == null || !VisibleText.hasVisibleCharacter(newName) || length(newName) > MAX_NAME_LENGTH) {
            throw new InvalidInputException("name", NAME_MESSAGE);
        }
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new InvalidInputException("username", USERNAME_MESSAGE);
        }
        String newBio = bio == null ? "" : LineBreaks.normalize(bio);
        if (length(newBio) > MAX_BIO_LENGTH) {
            throw new InvalidInputException("bio", BIO_MESSAGE);
        }
        // @ユーザーID を変えるときは、ほかの人と重ならないか確かめる（同時に同じ値にされたら、下の UNIQUE 制約で止まる）
        if (!username.equals(user.getUsername()) && userMapper.existsByUsername(username)) {
            throw usernameTaken();
        }
        byte[] avatarData = readAvatar(avatar);

        String oldKey = user.getAvatarKey();
        String newKey = oldKey;
        if (avatarData != null) {
            newKey = uploadAvatar(userId, avatarData);
        } else if (removeAvatar) {
            newKey = null;
        }

        try {
            userMapper.updateProfile(userId, newName, username, newBio, newKey);
        } catch (DuplicateKeyException e) {
            throw usernameTaken();
        }

        // 古いプロフィール画像は、DB の更新が確定してから S3 から消す。消せなくても編集は成功扱いにし、ログに残す
        if (oldKey != null && !Objects.equals(oldKey, newKey)) {
            AfterTransaction.afterCommit(() -> deleteQuietly(oldKey));
        }
        return profile(username, userId);
    }

    // 画像として正しいか（中身で判定）・大きさを確かめ、中身を返す。画像が送られていなければ null
    private static byte[] readAvatar(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            return null;
        }
        if (avatar.getSize() > MAX_AVATAR_BYTES) {
            throw new PayloadTooLargeException(AVATAR_TOO_LARGE);
        }
        try {
            return avatar.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // プロフィール画像を S3 に置き、キーを返す。DB の更新が取り消されたら、アップロードした画像を消す
    private String uploadAvatar(Long userId, byte[] data) {
        // 拡張子や Content-Type ではなく、ファイルの中身で判定する（投稿の画像と同じ）
        ImageType type = ImageType.detect(data)
                .orElseThrow(() -> new InvalidInputException("avatar", PostService.IMAGE_TYPE_MESSAGE));
        // キーには推測されにくい UUID を使い、元のファイル名は使わない
        String key = "avatars/%d/%s.%s".formatted(userId, UUID.randomUUID(), type.extension());
        try {
            imageStorage.put(key, data, type.contentType());
        } catch (RuntimeException e) {
            throw new StorageException("プロフィール画像のアップロードに失敗しました: " + key, e);
        }
        AfterTransaction.afterRollback(() -> deleteQuietly(key));
        return key;
    }

    private void deleteQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("S3 の画像を消せませんでした（あとで手で消す必要があります）: {}", key, e);
        }
    }

    private static ConflictException usernameTaken() {
        return new ConflictException(List.of(new FieldErrorResponse("username", USERNAME_TAKEN)));
    }

    // 絵文字も1文字と数える
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private UserProfileResponse toResponse(UserProfile profile, Long viewerId) {
        String avatarUrl = profile.getAvatarKey() == null ? null : imageStorage.publicUrl(profile.getAvatarKey());
        return new UserProfileResponse(profile.getUsername(), profile.getName(), profile.getBio(), avatarUrl,
                profile.getFollowingCount(), profile.getFollowerCount(), profile.isFollowedByMe(),
                profile.getId().equals(viewerId));
    }
}
