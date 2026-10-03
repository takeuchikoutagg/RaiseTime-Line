package com.example.raisetime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// SQL は src/main/resources/mapper/FollowMapper.xml
@Mapper
public interface FollowMapper {

    // フォローする。すでにフォロー中なら何もしない（戻り値は増えた行数：1 か 0）
    int insertIgnore(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    // フォローを解除する。フォローしていなければ何もしない（戻り値は消えた行数：1 か 0）
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    long countFollowers(Long userId);
}
