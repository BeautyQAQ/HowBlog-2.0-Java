package com.liushao.user.service;

import java.util.stream.Stream;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.liushao.user.pojo.ChangePasswordRequest;
import com.liushao.user.pojo.RegisterRequest;
import com.liushao.user.validation.PasswordValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PasswordValidationTest {
    @ParameterizedTest
    @MethodSource("passwordBoundaries")
    void dtoAndServiceEnforceCharacterAndUtf8Boundaries(String password, boolean expectedAcceptance) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            RegisterRequest registration = new RegisterRequest();
            registration.setMobile("13800000000");
            registration.setPassword(password);
            ChangePasswordRequest change = new ChangePasswordRequest();
            change.setOldPassword("old-password");
            change.setNewPassword(password);

            assertEquals(expectedAcceptance, PasswordValidator.isValidNewPassword(password));
            assertEquals(expectedAcceptance, validator.validate(registration).isEmpty());
            assertEquals(expectedAcceptance, validator.validate(change).isEmpty());
        }
    }

    private static Stream<Arguments> passwordBoundaries() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("        ", false),
                Arguments.of("a".repeat(7), false),
                Arguments.of("a".repeat(8), true),
                Arguments.of("a".repeat(64), true),
                Arguments.of("a".repeat(65), false),
                Arguments.of("汉".repeat(7), false),
                Arguments.of("汉".repeat(8), true),
                Arguments.of("汉".repeat(23) + "ab", true),
                Arguments.of("汉".repeat(24), true),
                Arguments.of("汉".repeat(24) + "a", false),
                Arguments.of("😀".repeat(18), true),
                Arguments.of("😀".repeat(18) + "a", false)
        );
    }
}
