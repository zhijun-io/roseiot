package io.roseiot.iam.infra.notifier;

public interface PasswordResetNotifier {

    void sendPasswordResetEmail(String email, String plainResetToken);
}
