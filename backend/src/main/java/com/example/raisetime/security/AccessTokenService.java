package com.example.raisetime.security;

import com.example.raisetime.config.AuthProperties;
import com.example.raisetime.config.JwtConfig;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

// アクセストークン（JWT）の発行と、JWT からログイン中ユーザーへの変換
@Service
public class AccessTokenService {

    static final String CLAIM_USERNAME = "username";
    static final String CLAIM_NAME = "name";

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    // sub（ユーザーの id）・@ユーザーID・名前を入れる。パスワードなどの秘密情報は入れない（JWT の中身は誰でも読めるため）
    public String issue(AuthUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_NAME, user.getName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public static AuthUser toAuthUser(Jwt jwt) {
        return new AuthUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(CLAIM_USERNAME),
                jwt.getClaimAsString(CLAIM_NAME), null);
    }
}
