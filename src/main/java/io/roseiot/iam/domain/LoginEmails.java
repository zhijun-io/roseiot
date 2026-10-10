package io.roseiot.iam.domain;

import java.util.Locale;

public final class LoginEmails {

    private LoginEmails() {}

    public static String normalize(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
