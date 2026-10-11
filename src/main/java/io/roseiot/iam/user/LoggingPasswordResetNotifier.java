package io.roseiot.iam.user;

import io.roseiot.system.SystemSettingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "roseiot.password-reset.logging.enabled", havingValue = "true")
class LoggingPasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final SystemSettingService systemSettingService;

    public LoggingPasswordResetNotifier(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @TransactionalEventListener
    public void sendPasswordResetEmail(PasswordResetRequestedEvent event) {
        try {
            String link = createLink(systemSettingService.requireGeneralBaseUrl(), event.token());
            log.warn("Local password-reset delivery for {} — open link: {}. Do not enable this notifier outside local development.",
                    event.email(), link);
        } catch (RuntimeException exception) {
            log.error("Local password-reset delivery failed", exception);
        }
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
                .path("/password-resets")
                .fragment("token={token}")
                .encode()
                .buildAndExpand(plainResetToken)
                .toUriString();
    }

    private static IllegalStateException invalidBaseUrl() {
        return new IllegalStateException("GENERAL setting baseUrl must be an absolute HTTP(S) URL");
    }
}
