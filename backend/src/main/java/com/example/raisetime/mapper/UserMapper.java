package com.example.raisetime.mapper;

import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// SQL は src/main/resources/mapper/UserMapper.xml
@Mapper
public interface UserMapper {

    User findByEmail(String email);

    User findById(Long id);

    User findByUsername(String username);

    // ユーザーページ（F-08）。フォロー数・フォロワー数・viewerId がフォロー中かを合わせて取る。無ければ null
    UserProfile findProfile(@Param("username") String username, @Param("viewerId") Long viewerId);

    // プロフィール編集（F-12）
    int updateProfile(@Param("id") Long id, @Param("name") String name, @Param("username") String username,
                      @Param("bio") String bio, @Param("avatarKey") String avatarKey);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // 採番された id は user.getId() に入る
    void insert(User user);
}
