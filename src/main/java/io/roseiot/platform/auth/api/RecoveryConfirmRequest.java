package io.roseiot.platform.auth.api;

import jakarta.validation.constraints.NotBlank;

public record RecoveryConfirmRequest(@NotBlank String token, @NotBlank String newPassword) {}
