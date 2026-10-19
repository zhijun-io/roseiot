package io.roseiot.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 256) String password) {}

record PasswordChangeRequest(
        @NotBlank @Size(max = 256) String currentPassword,
        @NotBlank @Size(max = 256) String newPassword) {}

record PasswordResetRequest(
        @NotBlank @Email @Size(max = 320) String email) {}

record PasswordResetCompletionRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(max = 256) String newPassword) {}

record LoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("refresh_expires_in") long refreshExpiresIn,
        @JsonProperty("session_id") long sessionId,
        @JsonProperty("password_change_required") boolean passwordChangeRequired) {
    static LoginResponse from(SessionLifecycle.SessionTokens tokens) {
        return new LoginResponse(
                tokens.accessToken(),
                "Bearer",
                tokens.expiresIn(),
                tokens.refreshToken(),
                tokens.refreshExpiresIn(),
                tokens.sessionId(),
                tokens.passwordChangeRequired());
    }
}

record PasswordChangeResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("password_change_required") boolean passwordChangeRequired) {}

record MessageResponse(String message) {}
