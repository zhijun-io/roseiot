package io.roseiot.iam.user;

import java.time.Instant;

record UserCredential(
        Long userId,
        String password,
        String resetToken,
        Instant resetExpiresAt,
        Instant resetAt,
        Instant createdAt,
        Instant updatedAt) {

    static UserCredential from(UserCredentialEntity entity) {
        return new UserCredential(entity.getUserId(), entity.getPassword(), entity.getResetToken(),
                entity.getResetExpiresAt(), entity.getResetAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "UserCredential[userId=" + userId + ", credentialData=redacted]";
    }
}
