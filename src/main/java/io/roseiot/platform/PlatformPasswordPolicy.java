package io.roseiot.platform;

import java.util.Optional;
import java.util.regex.Pattern;

public final class PlatformPasswordPolicy {

    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private PlatformPasswordPolicy() {}

    public static Optional<String> validationError(String password) {
        if (password == null || password.length() < 12) {
            return Optional.of("Password must be at least 12 characters");
        }
        if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
            return Optional.of("Password must contain letters and digits");
        }
        return Optional.empty();
    }
}
