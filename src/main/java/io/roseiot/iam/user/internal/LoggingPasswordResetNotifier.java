package io.roseiot.iam.user.internal;

import io.roseiot.iam.user.PasswordResetRequestedEvent;
import io.roseiot.system.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Locale;

@Component
class LoggingPasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final SystemSettingService systemSettingService;

    public LoggingPasswordResetNotifier(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @TransactionalEventListener
    public void sendPasswordResetEmail(PasswordResetRequestedEvent event) {
        String link = createLink(systemSettingService.requireGeneralBaseUrl(), event.token());
        log.warn("Password reset for {} — open link: {}", event.email(), link);
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
        return UriComponentsBuilder.fromUriString(base)
                .path("/password-resets/{token}")
                .encode()
                .buildAndExpand(plainResetToken)
                .toUriString();
    }

    private static IllegalStateException invalidBaseUrl() {
        return new IllegalStateException("GENERAL setting baseUrl must be an absolute HTTP(S) URL");
    }
}
