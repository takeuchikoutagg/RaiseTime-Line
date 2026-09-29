package com.example.raisetime.mapper;

import com.example.raisetime.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

// SQL は src/main/resources/mapper/RefreshTokenMapper.xml
@Mapper
public interface RefreshTokenMapper {

    void insert(RefreshToken token);

    RefreshToken findByTokenHash(String tokenHash);

    // まだ使えるトークンだけを無効にする。無効にできた件数（0 か 1）を返す。
    // 0 なら、同時に届いた別のリクエストが先に使ったということ
    int revoke(@Param("id") Long id, @Param("revokedAt") Instant revokedAt);

    int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

    int deleteExpired(Instant now);
}
