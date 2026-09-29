package com.example.raisetime.service;

import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AuthUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    static final String USERNAME_TAKEN = "この@ユーザーIDはすでに使われています";
    static final String EMAIL_TAKEN = "このメールアドレスはすでに登録されています";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // F-01 新規登録。登録したユーザーを返す（呼び出し元でそのままログイン状態にする）
    @Transactional
    public AuthUser signup(SignupRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        String username = request.username();

        List<FieldErrorResponse> conflicts = new ArrayList<>();
        if (userMapper.existsByUsername(username)) {
            conflicts.add(new FieldErrorResponse("username", USERNAME_TAKEN));
        }
        if (userMapper.existsByEmail(email)) {
            conflicts.add(new FieldErrorResponse("email", EMAIL_TAKEN));
        }
        if (!conflicts.isEmpty()) {
            throw new ConflictException(conflicts);
        }

        User user = new User(username, request.name().strip(), email, passwordEncoder.encode(request.password()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 上の確認のあと、同時に同じ値で登録された場合。DB の UNIQUE 制約で止まる
            throw new ConflictException(List.of(toConflictError(e)));
        }
        // セッションにはパスワードのハッシュを保存しない
        return new AuthUser(user.getId(), user.getUsername(), user.getName(), null);
    }

    // トークン更新のとき、最新のユーザー情報（名前など）でアクセストークンを作り直すために使う
    public Optional<AuthUser> findUser(Long id) {
        User user = userMapper.findById(id);
        return Optional.ofNullable(user)
                .map(u -> new AuthUser(u.getId(), u.getUsername(), u.getName(), null));
    }

    private static FieldErrorResponse toConflictError(DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains("uq_users_username")) {
            return new FieldErrorResponse("username", USERNAME_TAKEN);
        }
        return new FieldErrorResponse("email", EMAIL_TAKEN);
    }
}
