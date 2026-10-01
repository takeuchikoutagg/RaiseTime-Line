package com.example.raisetime.entity;

import java.time.Instant;

// post_images テーブルの1行。画像そのものではなく S3 のキーを持つ
public class PostImage {

    private Long id;
    private Long postId;
    private String s3Key;
    private int position;
    private Instant createdAt;

    public PostImage() {
    }

    public PostImage(Long postId, String s3Key, int position) {
        this.postId = postId;
        this.s3Key = s3Key;
        this.position = position;
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

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
