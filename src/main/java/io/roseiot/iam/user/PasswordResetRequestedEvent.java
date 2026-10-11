package io.roseiot.iam.user;

public record PasswordResetRequestedEvent(String email, String token) {}
