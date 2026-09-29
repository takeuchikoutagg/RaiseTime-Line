package com.example.raisetime.service;

import com.example.raisetime.config.AuthProperties;
import com.example.raisetime.entity.RefreshToken;
import com.example.raisetime.exception.InvalidRefreshTokenException;
import com.example.raisetime.mapper.RefreshTokenMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final UUID FAMILY = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                "test-secret-test-secret-test-secret-0123", Duration.ofMinutes(15), Duration.ofDays(7), false);
        service = new RefreshTokenService(refreshTokenMapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RefreshToken stored(Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(1L, "hash", FAMILY, expiresAt);
        token.setId(10L);
        token.setRevokedAt(revokedAt);
        return token;
    }

    @Test
    void issue_storesOnlyHash_withNewFamily_andSevenDayExpiry() {
        String raw = service.issue(1L);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insert(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw).hasSize(64);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getFamilyId()).isNotNull();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void issue_returnsDifferentTokensEachTime() {
        assertThat(service.issue(1L)).isNotEqualTo(service.issue(1L));
    }

    @Test
    void rotate_valid_revokesOld_andIssuesNewTokenInSameFamily() {
        when(refreshTokenMapper.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(stored(NOW.plusSeconds(60), null));
        when(refreshTokenMapper.revoke(10L, NOW)).thenReturn(1);

        RefreshTokenService.Rotation rotation = service.rotate("raw");

        assertThat(rotation.userId()).isEqualTo(1L);
        assertThat(rotation.newToken()).isNotEqualTo("raw");
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isEqualTo(FAMILY);
        assertThat(captor.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.newToken()));
    }

    @Test
    void rotate_unknownToken_throws() {
        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    void rotate_expiredToken_throws() {
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(stored(NOW, null));

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    void rotate_reusedToken_revokesWholeFamily_andThrows() {
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(stored(NOW.plusSeconds(60), NOW.minusSeconds(5)));

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenMapper).revokeFamily(FAMILY, NOW);
        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    void rotate_lostRaceWithConcurrentRequest_revokesWholeFamily_andThrows() {
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(stored(NOW.plusSeconds(60), null));
        when(refreshTokenMapper.revoke(anyLong(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenMapper).revokeFamily(FAMILY, NOW);
        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    void revoke_knownToken_revokesFamily() {
        when(refreshTokenMapper.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(stored(NOW.plusSeconds(60), null));

        service.revoke("raw");

        verify(refreshTokenMapper).revokeFamily(FAMILY, NOW);
    }

    @Test
    void revoke_unknownToken_doesNothing() {
        service.revoke("unknown");

        verify(refreshTokenMapper, never()).revokeFamily(any(), any());
    }
}
