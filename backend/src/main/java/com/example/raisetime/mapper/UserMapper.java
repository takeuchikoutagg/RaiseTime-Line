package com.example.raisetime.mapper;

import com.example.raisetime.entity.User;
import org.apache.ibatis.annotations.Mapper;

// SQL は src/main/resources/mapper/UserMapper.xml
@Mapper
public interface UserMapper {

    User findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // 採番された id は user.getId() に入る
    void insert(User user);
}
