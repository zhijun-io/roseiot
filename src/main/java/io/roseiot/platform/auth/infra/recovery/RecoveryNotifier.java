package io.roseiot.platform.auth.infra.recovery;

public interface RecoveryNotifier {

    void sendRecoveryEmail(String email, String plainToken);
}
