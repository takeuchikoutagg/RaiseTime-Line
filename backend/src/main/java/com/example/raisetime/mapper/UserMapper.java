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

    User findByUsername(String username);

    // ユーザーページ（F-08）用。viewerId は見ている人（フォロー中かどうかの判定に使う）
    UserProfile findProfile(@Param("username") String username, @Param("viewerId") Long viewerId);

    // ユーザー検索（F-09）。keyword は小文字にそろえ、LIKE の特殊文字（\ % _）をエスケープ済みのもの。
    // 並び順は「@ユーザーID の完全一致 → 前方一致 → その他」、同じ順位は @ユーザーID の昇順
    List<UserSummary> search(@Param("keyword") String keyword, @Param("viewerId") Long viewerId,
                             @Param("limit") int limit, @Param("offset") int offset);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // 採番された id は user.getId() に入る
    void insert(User user);
}
