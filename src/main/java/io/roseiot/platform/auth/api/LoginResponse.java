package io.roseiot.platform.auth.api;

public record LoginResponse(String accessToken, String refreshToken) {}
