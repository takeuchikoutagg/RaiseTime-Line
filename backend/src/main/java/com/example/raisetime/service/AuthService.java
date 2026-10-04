package com.example.raisetime.service;

import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.dto.UserResponse;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AuthUser;
import com.example.raisetime.storage.ImageStorage;
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
    private final ImageStorage imageStorage;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, ImageStorage imageStorage) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.imageStorage = imageStorage;
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

    // ログイン中のユーザー（login / refresh / me のレスポンス）。名前・@ユーザーID・プロフィール画像は DB の最新の値を返す。
    // ユーザーが削除されていたら空
    public Optional<UserResponse> describe(Long id) {
        return Optional.ofNullable(userMapper.findById(id))
                .map(u -> new UserResponse(u.getUsername(), u.getName(),
                        u.getAvatarKey() == null ? null : imageStorage.publicUrl(u.getAvatarKey())));
    }

    private static FieldErrorResponse toConflictError(DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains("uq_users_username")) {
            return new FieldErrorResponse("username", USERNAME_TAKEN);
        }
        return new FieldErrorResponse("email", EMAIL_TAKEN);
    }
}
