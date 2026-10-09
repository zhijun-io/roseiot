package io.roseiot.identity.infra.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final String linkBaseUrl;

    public LoggingPasswordResetNotifier(String linkBaseUrl) {
        this.linkBaseUrl = linkBaseUrl;
    }

    @Override
    public void sendPasswordResetEmail(String email, String plainResetToken) {
        String link = UriComponentsBuilder.fromUriString(linkBaseUrl)
                .path("/api/noauth/password-reset")
                .queryParam("resetToken", plainResetToken)
                .build()
                .toUriString();
        log.warn("Password reset for {} — open link: {}", email, link);
    }
}
