package com.example.raisetime.service;

import com.example.raisetime.dto.CommentListResponse;
import com.example.raisetime.dto.CommentResponse;
import com.example.raisetime.entity.Comment;
import com.example.raisetime.entity.CommentWithAuthor;
import com.example.raisetime.exception.InvalidInputException;
import com.example.raisetime.exception.NotFoundException;
import com.example.raisetime.mapper.CommentMapper;
import com.example.raisetime.mapper.PostMapper;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.storage.ImageStorage;
import com.example.raisetime.validation.NoControlCharactersValidator;
import com.example.raisetime.validation.VisibleText;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// F-05 コメント。自分の投稿にも他人の投稿にもコメントできる（フォローしていなくてもよい）
@Service
public class CommentService {

    static final int MAX_BODY_LENGTH = 280;
    static final int PAGE_SIZE = 20;
    static final String BODY_MESSAGE = "コメントは1〜280文字で入力してください";
    static final String BODY_CHARACTER_MESSAGE = "コメントに使えない文字が含まれています";

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final ImageStorage imageStorage;

    public CommentService(CommentMapper commentMapper, PostMapper postMapper, ImageStorage imageStorage) {
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
        this.imageStorage = imageStorage;
    }

    // 古い順に20件。続きは after（前回の最後のコメントの id）より新しいもの
    @Transactional(readOnly = true)
    public CommentListResponse list(Long postId, Long after) {
        requirePost(postId);
        // 1件多く取り、続きがあるかを判定する
        List<CommentWithAuthor> rows = commentMapper.findByPostId(postId, after, PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<CommentWithAuthor> page = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
        List<CommentResponse> items = page.stream().map(this::toResponse).toList();
        return new CommentListResponse(items, hasMore ? page.getLast().getId() : null);
    }

    @Transactional
    public CommentResponse create(Long postId, AuthUser author, String body) {
        validateBody(body);
        requirePost(postId);
        Comment comment = new Comment(postId, author.getId(), body);
        try {
            commentMapper.insert(comment);
        } catch (DataIntegrityViolationException e) {
            // 存在を確かめたあとに投稿が削除された（外部キー違反）
            throw new NotFoundException(PostService.POST_NOT_FOUND);
        }
        // コメントした人の名前・プロフィール画像は DB の最新の値を使うため、保存したコメントを取り直して返す
        return toResponse(commentMapper.findWithAuthorById(comment.getId()));
    }

    private static void validateBody(String body) {
        // 空白・見えない文字（ゼロ幅スペースなど）だけは不可。文字数は絵文字も1文字と数える（投稿の本文と同じ）
        if (!VisibleText.hasVisibleCharacter(body) || body.codePointCount(0, body.length()) > MAX_BODY_LENGTH) {
            throw new InvalidInputException("body", BODY_MESSAGE);
        }
        // NUL 文字などの制御文字（改行・タブ以外）は保存できない・表示できないので受け付けない
        if (NoControlCharactersValidator.containsControlCharacterExceptLineBreakAndTab(body)) {
            throw new InvalidInputException("body", BODY_CHARACTER_MESSAGE);
        }
    }

    private void requirePost(Long postId) {
        if (postMapper.findById(postId) == null) {
            throw new NotFoundException(PostService.POST_NOT_FOUND);
        }
    }

    private CommentResponse toResponse(CommentWithAuthor comment) {
        String avatarUrl = comment.getAuthorAvatarKey() == null ? null
                : imageStorage.publicUrl(comment.getAuthorAvatarKey());
        return new CommentResponse(comment.getId(), comment.getBody(), comment.getCreatedAt(),
                new CommentResponse.Author(comment.getAuthorUsername(), comment.getAuthorName(), avatarUrl));
    }
}
