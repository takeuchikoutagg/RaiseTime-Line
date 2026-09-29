package com.example.raisetime.security;

import com.example.raisetime.entity.User;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

// ログイン中のユーザー。セッション（Spring Session JDBC）にシリアライズして保存される。
// getUsername() は Spring Security の用語で「ユーザーを表す名前」で、このアプリでは @ユーザーID を返す。
// ログインに使うのはメールアドレス（AuthUserDetailsService#loadUserByUsername を参照）
public class AuthUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final Long id;
    private final String username;
    private final String name;
    private String passwordHash;

    public AuthUser(Long id, String username, String name, String passwordHash) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.passwordHash = passwordHash;
    }

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getName(), user.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    // 認証が終わったらパスワードのハッシュを消す（セッションに保存しないため）
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
