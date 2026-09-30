package com.example.raisetime.entity;

// 検索結果（F-09）などの一覧に出すユーザー
public class UserSummary {

    private Long id;
    private String username;
    private String name;
    // 見ている人（ログイン中のユーザー）がこのユーザーをフォローしているか
    private boolean followedByMe;

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

    public boolean isFollowedByMe() {
        return followedByMe;
    }

    public void setFollowedByMe(boolean followedByMe) {
        this.followedByMe = followedByMe;
    }
}
