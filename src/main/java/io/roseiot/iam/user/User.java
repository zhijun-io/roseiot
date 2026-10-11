package io.roseiot.iam.user;


/** Public user model; mutable database mappings remain private to the user feature. */
public record User(Long id, Long tenantId, String email, String name, UserStatus status) {
    static User from(UserEntity entity) {
        return new User(entity.getId(), entity.getTenantId(), entity.getEmail(), entity.getName(), entity.getStatus());
    }
}
