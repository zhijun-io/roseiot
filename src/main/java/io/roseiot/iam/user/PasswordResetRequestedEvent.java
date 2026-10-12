package io.roseiot.iam.user;

record PasswordResetRequestedEvent(String email, String token) {

    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[redacted]";
    }
}
