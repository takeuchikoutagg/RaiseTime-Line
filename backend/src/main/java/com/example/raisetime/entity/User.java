package com.example.raisetime.entity;

import java.time.Instant;

// users テーブルの1行。MyBatis が値を詰められるよう、引数なしコンストラクタと setter を持つ
public class User {

    private Long id;
    private String username;
    private String name;
    private String email;
    private String passwordHash;
    // 自己紹介文（F-12）。空でもよい
    private String bio;
    // プロフィール画像の S3 のキー（F-12）。設定していなければ null
    private String avatarKey;
    private Instant createdAt;
    private Instant updatedAt;

    public User() {
    }

    public User(String username, String name, String email, String passwordHash) {
        this.username = username;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }
}
