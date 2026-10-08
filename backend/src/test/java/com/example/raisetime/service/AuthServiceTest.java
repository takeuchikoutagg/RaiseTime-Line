package com.example.raisetime.service;

import com.example.raisetime.dto.UserResponse;
import com.example.raisetime.storage.ImageStorage;
import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private AuthService authService;

    private static SignupRequest request() {
        return new SignupRequest("  山田太郎 ", "taro_dev", " Taro@Example.COM ", "password123");
    }

    @Test
    void signup_normalizesEmailAndName_hashesPassword_andReturnsUserWithoutHash() {
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        doAnswer(invocation -> {
            invocation.<User>getArgument(0).setId(10L);
            return null;
        }).when(userMapper).insert(any(User.class));

        AuthUser result = authService.signup(request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("taro@example.com");
        assertThat(saved.getName()).isEqualTo("山田太郎");
        assertThat(saved.getUsername()).isEqualTo("taro_dev");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUsername()).isEqualTo("taro_dev");
        assertThat(result.getPassword()).isNull();
    }

    @Test
    void signup_duplicateUsernameAndEmail_throwsConflictWithBothFields() {
        when(userMapper.existsByUsername("taro_dev")).thenReturn(true);
        when(userMapper.existsByEmail("taro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrors())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(FieldErrorResponse.class))
                .extracting(FieldErrorResponse::field)
                .containsExactly("username", "email");
        verify(userMapper, never()).insert(any());
    }

    @Test
    void signup_uniqueViolationOnInsert_isConvertedToConflict() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
        doThrow(new DuplicateKeyException("duplicate key value violates unique constraint \"uq_users_username\""))
                .when(userMapper).insert(any(User.class));

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrors().getFirst().field())
                .isEqualTo("username");
    }

    // ---------- テスト技法から足したケース（docs/テスト設計書.md） ----------

    private static java.util.List<String> conflictFields(Throwable e) {
        return ((ConflictException) e).getErrors().stream().map(FieldErrorResponse::field).toList();
    }

    // T-14 デシジョンテーブル D-5 #2・#3：片方だけ使われているときは、その項目だけを返す
    @Test
    void signup_onlyUsernameTaken_reportsOnlyUsername() {
        when(userMapper.existsByUsername("taro_dev")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(conflictFields(e)).containsExactly("username"));
    }

    @Test
    void signup_onlyEmailTaken_reportsOnlyEmail() {
        when(userMapper.existsByEmail("taro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(conflictFields(e)).containsExactly("email"));
        verify(userMapper, never()).insert(any());
    }

    // T-14 D-5 #6：確認のあと、同時に同じメールアドレスで登録された（DB の UNIQUE 制約で止まる）
    @Test
    void signup_emailTakenAtTheSameTime_isConflictOnEmail() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
        doThrow(new DuplicateKeyException("duplicate key value violates unique constraint \"uq_users_email\""))
                .when(userMapper).insert(any(User.class));

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(conflictFields(e)).containsExactly("email"));
    }

    // T-23 分岐網羅：トークン更新・me で使う、最新のユーザー情報
    @Test
    void findUser_returnsUserWithoutPasswordHash_orEmptyWhenDeleted() {
        User user = new User("taro_dev", "山田太郎", "taro@example.com", "$2a$hashed");
        user.setId(1L);
        when(userMapper.findById(1L)).thenReturn(user);

        AuthUser found = authService.findUser(1L).orElseThrow();
        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getUsername()).isEqualTo("taro_dev");
        assertThat(found.getPassword()).isNull();

        assertThat(authService.findUser(99L)).isEmpty();
    }

    @Test
    void describe_returnsAvatarUrlOnlyWhenAvatarIsSet_orEmptyWhenDeleted() {
        User withoutAvatar = new User("taro_dev", "山田太郎", "taro@example.com", "$2a$hashed");
        User withAvatar = new User("hanako", "佐藤花子", "hanako@example.com", "$2a$hashed");
        withAvatar.setAvatarKey("avatars/2/a.png");
        when(userMapper.findById(1L)).thenReturn(withoutAvatar);
        when(userMapper.findById(2L)).thenReturn(withAvatar);
        when(imageStorage.publicUrl("avatars/2/a.png")).thenReturn("http://img/avatars/2/a.png");

        assertThat(authService.describe(1L)).contains(new UserResponse("taro_dev", "山田太郎", null));
        assertThat(authService.describe(2L)).contains(new UserResponse("hanako", "佐藤花子", "http://img/avatars/2/a.png"));
        assertThat(authService.describe(99L)).isEmpty();
    }
}
