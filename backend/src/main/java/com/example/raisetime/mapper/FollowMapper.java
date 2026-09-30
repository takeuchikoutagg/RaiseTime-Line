package com.example.raisetime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// SQL は src/main/resources/mapper/FollowMapper.xml
@Mapper
public interface FollowMapper {

    // すでにフォローしていれば何もしない。追加した件数（0 か 1）を返す
    int insertIfAbsent(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    // 削除した件数（0 か 1）を返す
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    boolean exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    long countFollowers(Long userId);

    long countFollowing(Long userId);
}
