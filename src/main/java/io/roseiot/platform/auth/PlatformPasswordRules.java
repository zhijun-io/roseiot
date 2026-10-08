package io.roseiot.platform.auth;

import java.util.regex.Pattern;

final class PlatformPasswordRules {

    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private PlatformPasswordRules() {}

    static void validateNewPassword(String password) {
        if (password == null || password.length() < 12) {
            throw new PasswordValidationException("Password must be at least 12 characters");
        }
        if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
            throw new PasswordValidationException("Password must contain letters and digits");
        }
    }
}
