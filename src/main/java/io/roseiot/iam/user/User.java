package io.roseiot.iam.user;

public record User(Long id, Long tenantId, String email, String name, UserStatus status) {

    static User from(UserEntity user) {
        return new User(user.getId(), user.getTenantId(), user.getEmail(), user.getName(), user.getStatus());
    }
}
