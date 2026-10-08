package io.roseiot.platform.auth.recovery;

public interface RecoveryNotificationPort {

    void sendRecoveryEmail(String email, String plainToken);
}
