package com.example.raisetime.security;

import com.example.raisetime.entity.User;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.service.EmailNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// ログイン時に、メールアドレスでユーザーを読み込む
@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public AuthUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    // 引数名は Spring Security の決まりで username だが、渡されるのはメールアドレス
    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userMapper.findByEmail(EmailNormalizer.normalize(email));
        if (user == null) {
            // どちらが間違っているかを伝えないよう、呼び出し元（DaoAuthenticationProvider）で BadCredentialsException に変わる
            throw new UsernameNotFoundException("user not found");
        }
        return AuthUser.from(user);
    }
}
