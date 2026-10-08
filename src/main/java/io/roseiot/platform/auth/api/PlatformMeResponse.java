package io.roseiot.platform.auth.api;

import java.util.UUID;

public record PlatformMeResponse(UUID id, String email) {}
