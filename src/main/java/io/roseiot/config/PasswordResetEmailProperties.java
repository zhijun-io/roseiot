package io.roseiot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("roseiot.password-reset.email")
public record PasswordResetEmailProperties(boolean enabled, String from) {

    public PasswordResetEmailProperties {
        if (enabled && (from == null || from.isBlank() || from.indexOf('\r') >= 0 || from.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException("A valid password-reset email sender is required when email is enabled");
        }
    }
}
