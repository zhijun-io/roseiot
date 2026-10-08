package io.roseiot.identity.infra.recovery;

public interface RecoveryNotifier {

    void sendRecoveryEmail(String email, String plainToken);
}
