package com.example.raisetime.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SignupRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static SignupRequest valid() {
        return new SignupRequest("山田太郎", "taro_dev", "taro@example.com", "password123");
    }

    private static Set<String> invalidFields(SignupRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequest_hasNoErrors() {
        assertThat(invalidFields(valid())).isEmpty();
    }

    @Test
    void name_emoji50_isValid_emoji51_isInvalid() {
        // 絵文字は String.length() では2文字だが、1文字と数える
        assertThat(invalidFields(new SignupRequest("😀".repeat(50), "taro_dev", "taro@example.com", "password123")))
                .isEmpty();
        assertThat(invalidFields(new SignupRequest("😀".repeat(51), "taro_dev", "taro@example.com", "password123")))
                .containsExactly("name");
    }

    @Test
    void name_blank_isInvalid() {
        assertThat(invalidFields(new SignupRequest("   ", "taro_dev", "taro@example.com", "password123")))
                .containsExactly("name");
    }

    @Test
    void username_mustBeLowercaseAlphanumericOrUnderscore_3to15() {
        assertThat(invalidFields(new SignupRequest("太郎", "ab", "taro@example.com", "password123")))
                .containsExactly("username");
        assertThat(invalidFields(new SignupRequest("太郎", "a".repeat(16), "taro@example.com", "password123")))
                .containsExactly("username");
        assertThat(invalidFields(new SignupRequest("太郎", "Taro", "taro@example.com", "password123")))
                .containsExactly("username");
        assertThat(invalidFields(new SignupRequest("太郎", "taro-dev", "taro@example.com", "password123")))
                .containsExactly("username");
        assertThat(invalidFields(new SignupRequest("太郎", "abc", "taro@example.com", "password123")))
                .isEmpty();
    }

    @Test
    void email_invalidFormat_isInvalid() {
        assertThat(invalidFields(new SignupRequest("太郎", "taro_dev", "not-an-email", "password123")))
                .containsExactly("email");
        assertThat(invalidFields(new SignupRequest("太郎", "taro_dev", "", "password123")))
                .containsExactly("email");
    }

    @Test
    void password_length8to64() {
        assertThat(invalidFields(new SignupRequest("太郎", "taro_dev", "taro@example.com", "1234567")))
                .containsExactly("password");
        assertThat(invalidFields(new SignupRequest("太郎", "taro_dev", "taro@example.com", "a".repeat(64))))
                .isEmpty();
        assertThat(invalidFields(new SignupRequest("太郎", "taro_dev", "taro@example.com", "a".repeat(65))))
                .containsExactly("password");
    }

    @Test
    void password_over72bytes_isInvalid_evenWithin64chars() {
        // 全角文字は UTF-8 で3バイト。25文字 = 75バイトで BCrypt の上限（72バイト）を超える
        assertThat(invalidFields(new SignupRequest("太郎", "taro_dev", "taro@example.com", "あ".repeat(25))))
                .containsExactly("password");
        assertThat(invalidFields(new SignupRequest("太郎", "taro_dev", "taro@example.com", "あ".repeat(24))))
                .isEmpty();
    }
}
