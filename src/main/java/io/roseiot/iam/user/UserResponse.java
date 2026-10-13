package io.roseiot.iam.user;

public record UserResponse(Long id, String email, String name, Long roleId, boolean disabled) {}
