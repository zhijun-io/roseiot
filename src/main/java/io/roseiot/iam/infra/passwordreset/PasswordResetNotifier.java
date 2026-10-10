package io.roseiot.iam.infra.passwordreset;

public interface PasswordResetNotifier {

    void sendPasswordResetEmail(String email, String plainResetToken);
}
