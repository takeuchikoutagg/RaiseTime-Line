package com.example.raisetime.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// 検証済みの JWT を、コントローラーで @AuthenticationPrincipal AuthUser として受け取れる形にする
@Component
public class AuthUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthUser user = AccessTokenService.toAuthUser(jwt);
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
