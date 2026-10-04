package com.example.raisetime.service;

// ほかのパッケージのテストから、UserService のパッケージ内の処理を使うための入り口
public final class UserServiceTestSupport {

    private UserServiceTestSupport() {
    }

    public static String escapeLike(String value) {
        return UserService.escapeLike(value);
    }
}
