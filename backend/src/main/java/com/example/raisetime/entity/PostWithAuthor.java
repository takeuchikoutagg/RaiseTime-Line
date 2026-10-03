package com.example.raisetime.entity;

// タイムライン・投稿詳細に出す投稿。posts と、投稿者（users）の @ユーザーID・名前、
// いいね数・コメント数・見ている人がいいね済みかを合わせたもの
public class PostWithAuthor extends Post {

    private String authorUsername;
    private String authorName;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;

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

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public boolean isLikedByMe() {
        return likedByMe;
    }

    public void setLikedByMe(boolean likedByMe) {
        this.likedByMe = likedByMe;
    }
}
