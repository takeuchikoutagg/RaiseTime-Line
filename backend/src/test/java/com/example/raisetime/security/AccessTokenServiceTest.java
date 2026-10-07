package com.example.raisetime.security;

import com.example.raisetime.config.AuthProperties;
import com.example.raisetime.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123";

    private final JwtConfig jwtConfig = new JwtConfig();
    private AccessTokenService service;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), false);
        SecretKey key = jwtConfig.jwtSecretKey(properties);
        service = new AccessTokenService(jwtConfig.jwtEncoder(key), properties, Clock.systemUTC());
        decoder = jwtConfig.jwtDecoder(key);
    }

    @Test
    void issue_containsUserClaims_andExpiresIn15Minutes() {
        String token = service.issue(new AuthUser(1L, "taro_dev", "山田太郎", "should-not-be-included"));

        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtConfig.ISSUER);
        assertThat(jwt.getClaimAsString("username")).isEqualTo("taro_dev");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("山田太郎");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        // パスワードのハッシュは入れない
        assertThat(jwt.getClaims().values()).doesNotContain("should-not-be-included");

        AuthUser user = AccessTokenService.toAuthUser(jwt);
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("taro_dev");
        assertThat(user.getName()).isEqualTo("山田太郎");
    }

    @Test
    void decode_tokenSignedWithOtherKey_isRejected() {
        AuthProperties other = new AuthProperties(
                "another-secret-another-secret-another-0123", Duration.ofMinutes(15), Duration.ofDays(7), false);
        SecretKey otherKey = jwtConfig.jwtSecretKey(other);
        String forged = new AccessTokenService(jwtConfig.jwtEncoder(otherKey), other, Clock.systemUTC())
                .issue(new AuthUser(1L, "taro_dev", "山田太郎", null));

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_expiredToken_isRejected() {
        AuthProperties properties = new AuthProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), false);
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), java.time.ZoneOffset.UTC);
        String expired = new AccessTokenService(jwtConfig.jwtEncoder(jwtConfig.jwtSecretKey(properties)),
                properties, twoHoursAgo).issue(new AuthUser(1L, "taro_dev", "山田太郎", null));

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void authProperties_rejectsShortSecret() {
        assertThatThrownBy(() -> new AuthProperties("too-short", Duration.ofMinutes(15), Duration.ofDays(7), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 境界値：31バイトは不可、32バイトは可。設定し忘れ（null）も起動させない
    @Test
    void authProperties_secretBoundary_andMissingSecret() {
        assertThatThrownBy(() -> new AuthProperties("a".repeat(31), Duration.ofMinutes(15), Duration.ofDays(7), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthProperties(null, Duration.ofMinutes(15), Duration.ofDays(7), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThat(new AuthProperties("a".repeat(32), Duration.ofMinutes(15), Duration.ofDays(7), false).jwtSecret())
                .hasSize(32);
    }
}
