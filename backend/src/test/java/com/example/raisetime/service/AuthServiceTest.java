package com.example.raisetime.service;

import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.dto.SignupRequest;
import com.example.raisetime.entity.User;
import com.example.raisetime.exception.ConflictException;
import com.example.raisetime.mapper.UserMapper;
import com.example.raisetime.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private static SignupRequest request() {
        return new SignupRequest("  山田太郎 ", "taro_dev", " Taro@Example.COM ", "password123");
    }

    @Test
    void signup_normalizesEmailAndName_hashesPassword_andReturnsUserWithoutHash() {
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        doAnswer(invocation -> {
            invocation.<User>getArgument(0).setId(10L);
            return null;
        }).when(userMapper).insert(any(User.class));

        AuthUser result = authService.signup(request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("taro@example.com");
        assertThat(saved.getName()).isEqualTo("山田太郎");
        assertThat(saved.getUsername()).isEqualTo("taro_dev");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUsername()).isEqualTo("taro_dev");
        assertThat(result.getPassword()).isNull();
    }

    @Test
    void signup_duplicateUsernameAndEmail_throwsConflictWithBothFields() {
        when(userMapper.existsByUsername("taro_dev")).thenReturn(true);
        when(userMapper.existsByEmail("taro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrors())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(FieldErrorResponse.class))
                .extracting(FieldErrorResponse::field)
                .containsExactly("username", "email");
        verify(userMapper, never()).insert(any());
    }

    @Test
    void signup_uniqueViolationOnInsert_isConvertedToConflict() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
        doThrow(new DuplicateKeyException("duplicate key value violates unique constraint \"uq_users_username\""))
                .when(userMapper).insert(any(User.class));

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrors().getFirst().field())
                .isEqualTo("username");
    }
}
