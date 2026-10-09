package io.roseiot.identity.infra.recovery;

public interface UserRecoveryNotifier {

    void sendRecoveryEmail(String email, String plainToken);
}
