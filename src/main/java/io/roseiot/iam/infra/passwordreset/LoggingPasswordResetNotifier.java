package io.roseiot.iam.infra.passwordreset;

import io.roseiot.system.application.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final SystemSettingService settings;

    public LoggingPasswordResetNotifier(SystemSettingService settings) {
        this.settings = settings;
    }

    @Override
    public void sendPasswordResetEmail(String email, String plainResetToken) {
        String link = createLink(settings.requireGeneralBaseUrl(), plainResetToken);
        log.warn("Password reset for {} — open link: {}", email, link);
    }

    static String createLink(String baseUrl, String plainResetToken) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw invalidBaseUrl();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || uri.getHost() == null || (!scheme.equals("http") && !scheme.equals("https"))
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalidBaseUrl();
        }

        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String resetUrl = UriComponentsBuilder.fromUriString(base)
                .path("/noauth/user-credentials/password-resets")
                .build()
                .toUriString();
        return resetUrl + "?resetToken=" + URLEncoder.encode(plainResetToken, StandardCharsets.UTF_8);
    }

    private static IllegalStateException invalidBaseUrl() {
        return new IllegalStateException("GENERAL setting baseUrl must be an absolute HTTP(S) URL");
    }
}
