package com.example.raisetime.mapper;

import com.example.raisetime.entity.User;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// SQL を手で書いているため、Docker の PostgreSQL（Flyway でテーブルを作成済み）に対して実際に実行して確認する。
// テストごとにロールバックされるので、データは残らない
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    private static User newUser(String username, String email) {
        return new User(username, "テスト", email, "$2a$10$hash");
    }

    @Test
    void insert_setsGeneratedId_andFindByEmailReturnsUser() {
        User user = newUser("mapper_test", "mapper-test@example.com");

        userMapper.insert(user);

        assertThat(user.getId()).isNotNull();
        User found = userMapper.findByEmail("mapper-test@example.com");
        assertThat(found.getId()).isEqualTo(user.getId());
        assertThat(found.getUsername()).isEqualTo("mapper_test");
        assertThat(found.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findById_returnsUser_orNullWhenUnknown() {
        User user = newUser("mapper_test", "mapper-test@example.com");
        userMapper.insert(user);

        assertThat(userMapper.findById(user.getId()).getUsername()).isEqualTo("mapper_test");
        assertThat(userMapper.findById(-1L)).isNull();
    }

    @Test
    void findByEmail_unknown_returnsNull() {
        assertThat(userMapper.findByEmail("nobody@example.com")).isNull();
    }

    @Test
    void existsByUsernameAndEmail() {
        userMapper.insert(newUser("mapper_test", "mapper-test@example.com"));

        assertThat(userMapper.existsByUsername("mapper_test")).isTrue();
        assertThat(userMapper.existsByUsername("other_user")).isFalse();
        assertThat(userMapper.existsByEmail("mapper-test@example.com")).isTrue();
        assertThat(userMapper.existsByEmail("other@example.com")).isFalse();
    }

    @Test
    void insert_duplicateEmail_throwsDuplicateKeyException() {
        userMapper.insert(newUser("mapper_test", "mapper-test@example.com"));

        assertThatThrownBy(() -> userMapper.insert(newUser("mapper_test2", "mapper-test@example.com")))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_users_email");
    }

    @Test
    void insert_invalidUsernameFormat_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> userMapper.insert(newUser("Bad-ID", "bad@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_username_format");
    }

    // ---------- プロフィール編集（F-12） ----------

    @Test
    void updateProfile_changesNameUsernameBioAndAvatar_andFindProfileReturnsThem() {
        User user = newUser("um_profile", "um_profile@example.com");
        userMapper.insert(user);
        assertThat(userMapper.findById(user.getId()).getBio()).isEmpty();
        assertThat(userMapper.findById(user.getId()).getAvatarKey()).isNull();

        assertThat(userMapper.updateProfile(user.getId(), "新しい名前", "um_renamed", "自己紹介😀", "avatars/x/a.png"))
                .isEqualTo(1);

        assertThat(userMapper.findByUsername("um_profile")).isNull();
        User found = userMapper.findByUsername("um_renamed");
        assertThat(found.getName()).isEqualTo("新しい名前");
        assertThat(found.getBio()).isEqualTo("自己紹介😀");
        assertThat(found.getAvatarKey()).isEqualTo("avatars/x/a.png");
        assertThat(userMapper.findProfile("um_renamed", user.getId()).getBio()).isEqualTo("自己紹介😀");
        assertThat(userMapper.findProfile("um_renamed", user.getId()).getAvatarKey()).isEqualTo("avatars/x/a.png");
    }

    @Test
    void updateProfile_bioOver160_isRejectedByCheckConstraint() {
        User user = newUser("um_bio", "um_bio@example.com");
        userMapper.insert(user);

        assertThatThrownBy(() -> userMapper.updateProfile(user.getId(), "名前", "um_bio", "😀".repeat(161), null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_bio_length");
    }

    @Test
    void updateProfile_usernameTaken_isRejectedByUniqueConstraint() {
        User a = newUser("um_a", "um_a@example.com");
        User b = newUser("um_b", "um_b@example.com");
        userMapper.insert(a);
        userMapper.insert(b);

        assertThatThrownBy(() -> userMapper.updateProfile(b.getId(), "名前", "um_a", "", null))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByIdForUpdate_returnsUserOrNull() {
        User user = newUser("um_lock", "um_lock@example.com");
        userMapper.insert(user);

        assertThat(userMapper.findByIdForUpdate(user.getId()).getUsername()).isEqualTo("um_lock");
        assertThat(userMapper.findByIdForUpdate(-1L)).isNull();
    }
}
