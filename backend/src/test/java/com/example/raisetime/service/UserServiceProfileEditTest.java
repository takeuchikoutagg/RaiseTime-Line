package com.example.raisetime.service;

import com.example.raisetime.dto.UserProfileResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.exception.PayloadTooLargeException;
import com.example.raisetime.exception.StorageException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.storage.ImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// F-12 プロフィール編集。トランザクションの完了後の処理（古いプロフィール画像の削除）を確かめるため、
// テストの中で TransactionSynchronizationManager を有効にし、コミット・ロールバックを手で起こしている
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceProfileEditTest {

    // 画像として最小限の正しい形（PNG：目印＋IEND チャンク、JPEG：SOI＋EOI）。サーバーはメタデータを取り除くときに中身を読む
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n',
        0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    @Mock
    private UserMapper userMapper;

    @Mock
    private PostService postService;

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        when(imageStorage.publicUrl(anyString())).thenAnswer(invocation -> "http://img/" + invocation.getArgument(0));
        // 編集後のプロフィールを返す（DB の代わり）
        when(userMapper.findProfile(anyString(), eq(1L))).thenAnswer(invocation -> {
            UserProfile profile = new UserProfile();
            profile.setId(1L);
            profile.setUsername(invocation.getArgument(0));
            profile.setName("取り直した名前");
            profile.setBio("");
            return profile;
        });
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

    private void currentUser(String avatarKey) {
        User user = new User("taro_dev", "山田太郎", "taro@example.com", "hash");
        user.setId(1L);
        user.setBio("");
        user.setAvatarKey(avatarKey);
        when(userMapper.findById(1L)).thenReturn(user);
    }

    private static MockMultipartFile avatar(byte[] data) {
        return new MockMultipartFile("avatar", "me.png", "image/png", data);
    }

    @Test
    void update_nameUsernameBio_savesAndReturnsUpdatedProfile() {
        currentUser(null);

        UserProfileResponse response = userService.updateProfile(1L, "  山田太郎（改）  ", "taro_new", "Javaを勉強中😀",
                null, false);

        // 名前の前後の空白は取り除く。プロフィール画像は変えない
        verify(userMapper).updateProfile(1L, "山田太郎（改）", "taro_new", "Javaを勉強中😀", null);
        assertThat(response.username()).isEqualTo("taro_new");
        assertThat(response.isMe()).isTrue();
    }

    @Test
    void update_sameUsername_doesNotCheckDuplicate() {
        currentUser(null);

        userService.updateProfile(1L, "山田太郎", "taro_dev", "", null, false);

        verify(userMapper, never()).existsByUsername(anyString());
        verify(userMapper).updateProfile(1L, "山田太郎", "taro_dev", "", null);
    }

    @Test
    void update_usernameTakenBySomeoneElse_isConflict() {
        currentUser(null);
        when(userMapper.existsByUsername("hanako")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(1L, "山田太郎", "hanako", "", null, false))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getErrors().getFirst().message())
                        .isEqualTo("この@ユーザーIDはすでに使われています"));
        verify(userMapper, never()).updateProfile(any(), any(), any(), any(), any());
    }

    @Test
    void update_usernameTakenAtTheSameTime_isConflict() {
        currentUser(null);
        doThrow(new DuplicateKeyException("uq_users_username"))
                .when(userMapper).updateProfile(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> userService.updateProfile(1L, "山田太郎", "taro_new", "", null, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_invalidInputs_areRejected_beforeSaving() {
        currentUser(null);

        assertThatThrownBy(() -> userService.updateProfile(1L, " ​ ", "taro_dev", "", null, false))
                .isInstanceOf(InvalidInputException.class).hasMessage("名前は1〜50文字で入力してください");
        assertThatThrownBy(() -> userService.updateProfile(1L, "あ".repeat(51), "taro_dev", "", null, false))
                .isInstanceOf(InvalidInputException.class).hasMessage("名前は1〜50文字で入力してください");
        for (String username : new String[] {null, "ab", "Taro", "taro-dev", "a".repeat(16)}) {
            assertThatThrownBy(() -> userService.updateProfile(1L, "山田太郎", username, "", null, false))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessage("@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください");
        }
        assertThatThrownBy(() -> userService.updateProfile(1L, "山田太郎", "taro_dev", "😀".repeat(161), null, false))
                .isInstanceOf(InvalidInputException.class).hasMessage("自己紹介は160文字以内で入力してください");
        verify(userMapper, never()).updateProfile(any(), any(), any(), any(), any());
    }

    @Test
    void update_bio160Emoji_andEmptyBio_areAccepted() {
        currentUser(null);

        userService.updateProfile(1L, "山田太郎", "taro_dev", "😀".repeat(160), null, false);
        userService.updateProfile(1L, "山田太郎", "taro_dev", null, null, false);

        verify(userMapper).updateProfile(1L, "山田太郎", "taro_dev", "😀".repeat(160), null);
        verify(userMapper).updateProfile(1L, "山田太郎", "taro_dev", "", null);
    }

    @Test
    void update_newAvatar_uploadsWithUuidKey_andDeletesOldAvatarOnlyAfterCommit() {
        currentUser("avatars/1/old.png");

        UserProfileResponse response = userService.updateProfile(1L, "山田太郎", "taro_dev", "", avatar(PNG), false);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(imageStorage).put(key.capture(), any(), eq("image/png"));
        assertThat(key.getValue()).matches("avatars/1/[0-9a-f-]{36}\\.png");
        verify(userMapper).updateProfile(1L, "山田太郎", "taro_dev", "", key.getValue());
        assertThat(response.username()).isEqualTo("taro_dev");
        // 古い画像は DB の更新が確定してから消す
        verify(imageStorage, never()).delete(anyString());
        commit();
        verify(imageStorage).delete("avatars/1/old.png");
        verify(imageStorage, never()).delete(key.getValue());
    }

    @Test
    void update_rolledBack_deletesNewlyUploadedAvatar_andKeepsOldOne() {
        currentUser("avatars/1/old.png");

        userService.updateProfile(1L, "山田太郎", "taro_dev", "", avatar(PNG), false);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(imageStorage).put(key.capture(), any(), anyString());
        rollback();

        verify(imageStorage).delete(key.getValue());
        verify(imageStorage, never()).delete("avatars/1/old.png");
    }

    @Test
    void update_removeAvatar_clearsKey_andDeletesOldAvatarAfterCommit() {
        currentUser("avatars/1/old.png");

        userService.updateProfile(1L, "山田太郎", "taro_dev", "", null, true);

        verify(userMapper).updateProfile(1L, "山田太郎", "taro_dev", "", null);
        commit();
        verify(imageStorage).delete("avatars/1/old.png");
    }

    @Test
    void update_withoutAvatarChange_keepsCurrentAvatar() {
        currentUser("avatars/1/old.png");

        userService.updateProfile(1L, "山田太郎", "taro_dev", "", null, false);

        verify(userMapper).updateProfile(1L, "山田太郎", "taro_dev", "", "avatars/1/old.png");
        commit();
        verify(imageStorage, never()).delete(anyString());
    }

    @Test
    void update_avatarNotAnImage_orTooLarge_isRejected() {
        currentUser(null);

        assertThatThrownBy(() -> userService.updateProfile(1L, "山田太郎", "taro_dev", "",
                avatar("not an image".getBytes()), false))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("JPEG・PNG・GIF・WebP の画像を選んでください");
        // プロフィール画像は2MBまで（投稿の画像の5MBより小さい）
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> userService.updateProfile(1L, "山田太郎", "taro_dev", "", avatar(big), false))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("プロフィール画像は2MBまでです");
        verify(imageStorage, never()).put(anyString(), any(), anyString());
        verify(userMapper, never()).updateProfile(any(), any(), any(), any(), any());
    }

    @Test
    void update_uploadFails_isStorageException() {
        currentUser(null);
        doThrow(new RuntimeException("S3 down")).when(imageStorage).put(anyString(), any(), anyString());

        assertThatThrownBy(() -> userService.updateProfile(1L, "山田太郎", "taro_dev", "", avatar(PNG), false))
                .isInstanceOf(StorageException.class);
        verify(userMapper, never()).updateProfile(any(), any(), any(), any(), any());
    }

    @Test
    void update_crlfLineBreaksInBio_areSavedAsLf_andCountedAsOneCharacter() {
        currentUser(null);
        // 画面では「あ×80 ＋ 改行 ＋ あ×79」= 160文字
        String bio = "あ".repeat(80) + "\r\n" + "あ".repeat(79);

        userService.updateProfile(1L, "山田太郎", "taro_dev", bio, null, false);

        verify(userMapper).updateProfile(1L, "山田太郎", "taro_dev", "あ".repeat(80) + "\n" + "あ".repeat(79), null);
    }

    @Test
    void update_avatarJustUnder2MB_isAccepted() {
        currentUser(null);
        // JPEG は EOI のあとのデータを画像データとしてそのまま残すので、2MB ちょうどの大きさを作りやすい
        byte[] justUnder = new byte[2 * 1024 * 1024];
        justUnder[0] = (byte) 0xFF;
        justUnder[1] = (byte) 0xD8;
        justUnder[2] = (byte) 0xFF;
        justUnder[3] = (byte) 0xD9;

        userService.updateProfile(1L, "山田太郎", "taro_dev", "", avatar(justUnder), false);

        verify(imageStorage).put(anyString(), any(), eq("image/jpeg"));
    }

    @Test
    void update_avatarGpsMetadata_isRemovedBeforeUpload() {
        currentUser(null);
        // SOI ＋ APP1（EXIF に位置情報）＋ EOI
        byte[] exif = "Exif\0\0GPSLatitude".getBytes(StandardCharsets.ISO_8859_1);
        byte[] jpeg = new byte[4 + 4 + exif.length];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        jpeg[3] = (byte) 0xE1;
        jpeg[4] = 0;
        jpeg[5] = (byte) (exif.length + 2);
        System.arraycopy(exif, 0, jpeg, 6, exif.length);
        jpeg[6 + exif.length] = (byte) 0xFF;
        jpeg[7 + exif.length] = (byte) 0xD9;

        userService.updateProfile(1L, "山田太郎", "taro_dev", "", avatar(jpeg), false);

        ArgumentCaptor<byte[]> uploaded = ArgumentCaptor.forClass(byte[].class);
        verify(imageStorage).put(anyString(), uploaded.capture(), eq("image/jpeg"));
        assertThat(uploaded.getValue()).containsExactly((byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9);
    }
}
