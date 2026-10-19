package io.roseiot.auth;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final PasswordResetCredentialService credentials;
    private final PasswordResetEmailSender emailSender;
    private final AuthMailProperties mailProperties;
    private final AuthAttemptThrottle throttle;

    PasswordResetService(
            PasswordResetCredentialService credentials,
            PasswordResetEmailSender emailSender,
            AuthMailProperties mailProperties,
            AuthAttemptThrottle throttle) {
        this.credentials = credentials;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
        this.throttle = throttle;
    }

    void request(String email) {
        if (!throttle.allow("password-reset", AuthenticationService.normalizeEmail(email), 3, Duration.ofHours(1))) {
            return;
        }
        var delivery = credentials.createResetCredential(email);
        if (delivery == null) {
            return;
        }
        String resetUrl =
                mailProperties.baseUrl().replaceAll("/+$", "") + "/reset-password?token=" + delivery.rawToken();
        try {
            emailSender.send(delivery.recipient(), resetUrl);
        } catch (MailException failure) {
            credentials.invalidate(delivery.tokenId());
            log.warn("Password reset email handoff failed; no account information was included");
        }
    }

    void complete(String token, String newPassword, String sourceAddress) {
        if (!throttle.allow("password-reset-completion", sourceAddress, 10, Duration.ofMinutes(15))) {
            throw new RateLimitedException();
        }
        credentials.completeReset(token, newPassword);
    }
}
