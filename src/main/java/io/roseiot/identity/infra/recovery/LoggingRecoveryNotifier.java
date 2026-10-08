package io.roseiot.identity.infra.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingRecoveryNotifier implements RecoveryNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingRecoveryNotifier.class);

    @Override
    public void sendRecoveryEmail(String email, String plainToken) {
        log.warn("System user recovery requested for email {}", email);
    }
}
