package com.example.raisetime.mapper;

import com.example.raisetime.entity.User;
import com.example.raisetime.entity.UserProfile;
import com.example.raisetime.entity.UserSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// SQL は src/main/resources/mapper/UserMapper.xml
@Mapper
public interface UserMapper {

    User findByEmail(String email);

    User findById(Long id);

    // プロフィール編集（F-12）のために、行をロックして取る（同じユーザーの編集が同時に走らないようにする）。無ければ null
    User findByIdForUpdate(Long id);

    User findByUsername(String username);

    // ユーザーページ（F-08）。フォロー数・フォロワー数・viewerId がフォロー中かを合わせて取る。無ければ null
    UserProfile findProfile(@Param("username") String username, @Param("viewerId") Long viewerId);

    // プロフィール編集（F-12）
    int updateProfile(@Param("id") Long id, @Param("name") String name, @Param("username") String username,
                      @Param("bio") String bio, @Param("avatarKey") String avatarKey);

    // ユーザー検索（F-09）。@ユーザーID か名前に keyword が含まれる人を、@ID の完全一致 → 前方一致 → その他、
    // 同じ順位は @ID の昇順で、offset 件目から limit 件。
    // keyword：小文字にそろえたキーワード（完全一致の比較に使う）、pattern：LIKE 用に % _ \ をエスケープしたキーワード
    List<UserSummary> search(@Param("keyword") String keyword, @Param("pattern") String pattern,
                             @Param("viewerId") Long viewerId, @Param("limit") int limit,
                             @Param("offset") int offset);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // 採番された id は user.getId() に入る
    void insert(User user);
}
