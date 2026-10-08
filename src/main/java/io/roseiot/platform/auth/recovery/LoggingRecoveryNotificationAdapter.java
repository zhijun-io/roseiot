package io.roseiot.platform.auth.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class LoggingRecoveryNotificationAdapter implements RecoveryNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingRecoveryNotificationAdapter.class);

    @Override
    public void sendRecoveryEmail(String email, String plainToken) {
        log.warn("Platform admin recovery requested for email {}", email);
    }
}
