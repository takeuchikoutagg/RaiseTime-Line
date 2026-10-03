package com.example.raisetime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// SQL は src/main/resources/mapper/LikeMapper.xml
@Mapper
public interface LikeMapper {

    // いいねする。すでにいいね済みなら何もしない（戻り値は増えた行数：1 か 0）
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);

    // いいねを取り消す。いいねしていなければ何もしない（戻り値は消えた行数：1 か 0）
    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    long countByPostId(Long postId);

    boolean exists(@Param("postId") Long postId, @Param("userId") Long userId);
}
