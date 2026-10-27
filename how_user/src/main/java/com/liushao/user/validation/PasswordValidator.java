package com.liushao.user.validation;

import java.nio.charset.StandardCharsets;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    public static final int MAX_BCRYPT_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isValidNewPassword(value);
    }

    public static boolean isValidNewPassword(String value) {
        return StringUtils.hasText(value) && value.length() >= 8 && value.length() <= 64
                && fitsBcrypt(value);
    }

    public static boolean fitsBcrypt(String value) {
        return value != null && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
    }
}
