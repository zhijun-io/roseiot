package io.roseiot.auth;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.mail")
record AuthMailProperties(
        @NotBlank String baseUrl, @NotBlank String from) {
    AuthMailProperties {
        if (baseUrl != null && !baseUrl.isBlank()) {
            URI uri = URI.create(baseUrl);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Mail base URL must be an absolute origin or path without query");
            }
        }
    }
}
