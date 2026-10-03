package com.example.raisetime.entity;

import java.time.Instant;

// comments テーブルの1行
public class Comment {

    private Long id;
    private Long postId;
    private Long userId;
    private String body;
    private Instant createdAt;
    private Instant updatedAt;

    public Comment() {
    }

    public Comment(Long postId, Long userId, String body) {
        this.postId = postId;
        this.userId = userId;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
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
}
