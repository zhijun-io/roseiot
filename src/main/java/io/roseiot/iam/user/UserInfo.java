package io.roseiot.iam.user;

public record UserInfo(Long id, String email, String name, Long roleId, boolean disabled) {}
