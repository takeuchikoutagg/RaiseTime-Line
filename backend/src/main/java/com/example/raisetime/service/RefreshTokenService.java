package com.example.raisetime.service;

import com.example.raisetime.config.AuthProperties;
import com.example.raisetime.entity.RefreshToken;
import com.example.raisetime.exception.InvalidRefreshTokenException;
import com.example.raisetime.logging.LogContext;
import com.example.raisetime.logging.LogEvents;
import com.example.raisetime.mapper.RefreshTokenMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

// リフレッシュトークンの発行・交換（ローテーション）・無効化
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenMapper refreshTokenMapper;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenMapper refreshTokenMapper, AuthProperties properties, Clock clock) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public record Rotation(Long userId, String newToken) {
    }

    // ログインしたとき：新しい系列でトークンを発行する。DB にはハッシュだけを保存し、トークンそのものを返す
    @Transactional
    public String issue(Long userId) {
        return issue(userId, UUID.randomUUID());
    }

    // トークンを使って新しいトークンに交換する。使ったトークンは無効にする。
    // 無効にしたあとの例外でも、系列の無効化は取り消さない（noRollbackFor）
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenMapper.findByTokenHash(hash(rawToken));
        if (token == null) {
            throw new InvalidRefreshTokenException("unknown refresh token");
        }
        if (token.getRevokedAt() != null) {
            // 使用済みのトークンがもう一度使われた ＝ 盗まれた可能性がある。その系列をまとめて無効にする
            revokeFamilyAfterReuse(token, now);
            throw new InvalidRefreshTokenException("refresh token reused");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException("refresh token expired");
        }
        if (refreshTokenMapper.revoke(token.getId(), now) == 0) {
            // 確認と無効化のあいだに、別のリクエストが先に使った
            revokeFamilyAfterReuse(token, now);
            throw new InvalidRefreshTokenException("refresh token reused concurrently");
        }
        return new Rotation(token.getUserId(), issue(token.getUserId(), token.getFamilyId()));
    }

    // ログアウト：そのトークンの系列をまとめて無効にする。知らないトークンなら何もしない
    @Transactional
    public void revoke(String rawToken) {
        RefreshToken token = refreshTokenMapper.findByTokenHash(hash(rawToken));
        if (token != null) {
            refreshTokenMapper.revokeFamily(token.getFamilyId(), clock.instant());
        }
    }

    // 期限切れのトークンを毎日4時（UTC）に削除する
    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    @Transactional
    public void deleteExpired() {
        int deleted = refreshTokenMapper.deleteExpired(clock.instant());
        log.atInfo()
                .addKeyValue(LogContext.EVENT, LogEvents.REFRESH_TOKENS_PURGED)
                .addKeyValue("deleted_count", deleted)
                .log("期限切れのリフレッシュトークンを {} 件削除しました", deleted);
    }

    private String issue(Long userId, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        refreshTokenMapper.insert(new RefreshToken(userId, hash(rawToken), familyId, expiresAt));
        return rawToken;
    }

    private void revokeFamilyAfterReuse(RefreshToken token, Instant now) {
        int revoked = refreshTokenMapper.revokeFamily(token.getFamilyId(), now);
        // 盗まれたトークンが使われた可能性がある。監視（docs/ログ・監視設計.md の M-6）はこの event で気づく
        log.atWarn()
                .addKeyValue(LogContext.EVENT, LogEvents.REFRESH_TOKEN_REUSED)
                .addKeyValue(LogContext.USER_ID, token.getUserId())
                .addKeyValue("revoked_count", revoked)
                .log("使用済みのリフレッシュトークンが使われたため、系列を無効にしました（userId={}, 無効にした件数={}）",
                        token.getUserId(), revoked);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
