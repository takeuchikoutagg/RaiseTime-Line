package com.example.raisetime.mapper;

import com.example.raisetime.entity.Comment;
import com.example.raisetime.entity.CommentWithAuthor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// SQL は src/main/resources/mapper/CommentMapper.xml
@Mapper
public interface CommentMapper {

    // 採番された id と、DB が入れた created_at は comment に入る
    void insert(Comment comment);

    // 投稿のコメントを古い順（id の小さい順）に limit 件。after があれば、それより新しいもの
    List<CommentWithAuthor> findByPostId(@Param("postId") Long postId, @Param("after") Long after,
                                         @Param("limit") int limit);
}
