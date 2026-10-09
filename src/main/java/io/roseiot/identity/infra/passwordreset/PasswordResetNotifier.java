package io.roseiot.identity.infra.passwordreset;

public interface PasswordResetNotifier {

    void sendPasswordResetEmail(String email, String plainResetToken);
}
