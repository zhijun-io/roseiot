package io.roseiot.platform.auth.api;

import jakarta.validation.constraints.NotBlank;

public record RecoveryRequest(@NotBlank String email) {}
