package com.example.raisetime.entity;

// 投稿詳細に出すコメント。comments と、コメントした人（users）の @ユーザーID・名前を合わせたもの
public class CommentWithAuthor extends Comment {

    private String authorUsername;
    private String authorName;
    // 投稿・コメントした人のプロフィール画像の S3 のキー。設定していなければ null
    private String authorAvatarKey;

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

    public String getAuthorAvatarKey() {
        return authorAvatarKey;
    }

    public void setAuthorAvatarKey(String authorAvatarKey) {
        this.authorAvatarKey = authorAvatarKey;
    }
}
