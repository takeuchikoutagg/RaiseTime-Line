package com.example.raisetime.dto;

import com.example.raisetime.security.AuthUser;

// signup / login / me のレスポンス
public record UserResponse(String username, String name) {

    public static UserResponse from(AuthUser user) {
        return new UserResponse(user.getUsername(), user.getName());
    }
}
