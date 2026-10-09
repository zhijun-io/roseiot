package io.roseiot.identity.infra.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingUserRecoveryNotifier implements UserRecoveryNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingUserRecoveryNotifier.class);

    @Override
    public void sendRecoveryEmail(String email, String plainToken) {
        log.warn("System user recovery requested for email {}", email);
    }
}
