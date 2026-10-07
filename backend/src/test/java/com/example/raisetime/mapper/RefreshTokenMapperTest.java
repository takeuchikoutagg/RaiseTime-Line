package com.example.raisetime.mapper;

import com.example.raisetime.entity.RefreshToken;
import com.example.raisetime.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Docker の PostgreSQL に対して実際に SQL を実行する。テストごとにロールバックされる
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenMapperTest {

    // PostgreSQL の timestamptz はマイクロ秒までなので、比べやすいよう秒で切り捨てる
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Autowired
    private UserMapper userMapper;

    private Long userId;

    @BeforeEach
    void createUser() {
        User user = new User("rt_mapper_test", "テスト", "rt-mapper-test@example.com", "$2a$10$hash");
        userMapper.insert(user);
        userId = user.getId();
    }

    private RefreshToken insert(String hash, UUID family, Instant expiresAt) {
        RefreshToken token = new RefreshToken(userId, hash, family, expiresAt);
        refreshTokenMapper.insert(token);
        return token;
    }

    private static String hash(char c) {
        return String.valueOf(c).repeat(64);
    }

    @Test
    void insert_andFindByTokenHash_roundTripsAllColumns_includingUuid() {
        UUID family = UUID.randomUUID();
        RefreshToken inserted = insert(hash('a'), family, NOW.plusSeconds(3600));

        RefreshToken found = refreshTokenMapper.findByTokenHash(hash('a'));

        assertThat(found.getId()).isEqualTo(inserted.getId()).isNotNull();
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getFamilyId()).isEqualTo(family);
        assertThat(found.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findByTokenHash_unknown_returnsNull() {
        assertThat(refreshTokenMapper.findByTokenHash(hash('z'))).isNull();
    }

    @Test
    void revoke_onlyOnce_secondCallReturnsZero() {
        RefreshToken token = insert(hash('a'), UUID.randomUUID(), NOW.plusSeconds(3600));

        assertThat(refreshTokenMapper.revoke(token.getId(), NOW)).isEqualTo(1);
        assertThat(refreshTokenMapper.revoke(token.getId(), NOW)).isZero();
        assertThat(refreshTokenMapper.findByTokenHash(hash('a')).getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revokeFamily_revokesOnlyActiveTokensOfThatFamily() {
        UUID family = UUID.randomUUID();
        RefreshToken used = insert(hash('a'), family, NOW.plusSeconds(3600));
        refreshTokenMapper.revoke(used.getId(), NOW.minusSeconds(60));
        insert(hash('b'), family, NOW.plusSeconds(3600));
        insert(hash('c'), UUID.randomUUID(), NOW.plusSeconds(3600));

        assertThat(refreshTokenMapper.revokeFamily(family, NOW)).isEqualTo(1);
        assertThat(refreshTokenMapper.findByTokenHash(hash('a')).getRevokedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(refreshTokenMapper.findByTokenHash(hash('b')).getRevokedAt()).isEqualTo(NOW);
        assertThat(refreshTokenMapper.findByTokenHash(hash('c')).getRevokedAt()).isNull();
    }

    @Test
    void deleteExpired_deletesOnlyExpiredTokens() {
        // 開発用の DB に入っているトークン（本当に期限が切れたものもある）を数えないよう、ずっと昔の時刻を「今」にする。
        // 以前は本当の現在時刻を使っていて、DB に期限切れのトークンがあると件数がずれて失敗した
        Instant now = Instant.parse("2000-01-01T00:00:00Z");
        insert(hash('a'), UUID.randomUUID(), now.minusSeconds(1));
        // 境界値：ちょうど今が期限のものも、期限切れとして消す
        insert(hash('b'), UUID.randomUUID(), now);
        insert(hash('c'), UUID.randomUUID(), now.plusSeconds(1));

        assertThat(refreshTokenMapper.deleteExpired(now)).isEqualTo(2);
        assertThat(refreshTokenMapper.findByTokenHash(hash('a'))).isNull();
        assertThat(refreshTokenMapper.findByTokenHash(hash('b'))).isNull();
        assertThat(refreshTokenMapper.findByTokenHash(hash('c'))).isNotNull();
    }

    @Test
    void insert_duplicateHash_throwsDuplicateKeyException() {
        insert(hash('a'), UUID.randomUUID(), NOW.plusSeconds(3600));

        assertThatThrownBy(() -> insert(hash('a'), UUID.randomUUID(), NOW.plusSeconds(3600)))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
