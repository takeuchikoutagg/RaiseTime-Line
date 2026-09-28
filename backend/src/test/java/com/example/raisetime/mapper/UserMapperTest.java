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
}
