package io.roseiot.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.jwt")
record AuthJwtProperties(
        @NotBlank String hmacSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration resetTokenTtl) {
    AuthJwtProperties {
        if (accessTokenTtl != null && (accessTokenTtl.isNegative() || accessTokenTtl.isZero())) {
            throw new IllegalArgumentException("Access token lifetime must be positive");
        }
        if (refreshTokenTtl != null && (refreshTokenTtl.isNegative() || refreshTokenTtl.isZero())) {
            throw new IllegalArgumentException("Refresh token lifetime must be positive");
        }
        if (resetTokenTtl != null && (resetTokenTtl.isNegative() || resetTokenTtl.isZero())) {
            throw new IllegalArgumentException("Password reset token lifetime must be positive");
        }
    }
}
