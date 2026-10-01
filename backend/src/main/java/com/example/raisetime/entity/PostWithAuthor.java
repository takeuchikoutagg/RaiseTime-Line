package com.example.raisetime.entity;

// タイムラインなどに出す投稿。posts と、投稿者（users）の @ユーザーID・名前を合わせたもの
public class PostWithAuthor extends Post {

    private String authorUsername;
    private String authorName;

    public String getAuthorUsername() {
        return authorUsername;
    }

    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }
}
