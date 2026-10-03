package com.example.raisetime.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// null は「未入力」として、ほかのチェック（@NotBlank など）に任せる
public class NoControlCharactersValidator implements ConstraintValidator<NoControlCharacters, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !containsControlCharacter(value);
    }

    public static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
