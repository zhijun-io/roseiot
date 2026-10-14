package io.roseiot.iam.user;

import java.net.URI;
import java.util.Locale;
import org.springframework.web.util.UriComponentsBuilder;

final class PasswordResetLink {

    private PasswordResetLink() {}

    static String create(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw invalidBaseUrl();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || (!scheme.equals("http") && !scheme.equals("https"))
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalidBaseUrl();
        }

        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return UriComponentsBuilder.fromUriString(base)
                .path("/password-resets")
                .encode()
                .build()
                .toUriString();
    }

    private static IllegalStateException invalidBaseUrl() {
        return new IllegalStateException("GENERAL setting baseUrl must be an absolute HTTP(S) URL");
    }
}
