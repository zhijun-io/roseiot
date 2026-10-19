package io.roseiot.auth;

interface PasswordResetEmailSender {
    void send(String recipient, String resetUrl);
}
