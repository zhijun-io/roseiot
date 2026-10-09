package io.roseiot.identity.web;

import io.roseiot.identity.domain.ActivationNotImplementedException;
import io.roseiot.identity.domain.InvalidCredentialsException;
import io.roseiot.identity.domain.InvalidResetTokenException;
import io.roseiot.identity.domain.InvalidRefreshTokenException;
import io.roseiot.identity.domain.PasswordValidationException;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.api.ApiErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.roseiot.identity.web")
@Order(0)
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class IdentityExceptionHandler {

    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public IdentityExceptionHandler(ApiErrorBodyWriter apiErrorBodyWriter) {
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.UNAUTHORIZED.value(),
                "invalid_credentials",
                "Invalid email or password");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.UNAUTHORIZED.value(),
                "invalid_refresh_token",
                "Invalid refresh token");
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidResetToken(InvalidResetTokenException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.BAD_REQUEST.value(),
                "invalid_reset_token",
                "Invalid reset token");
    }

    @ExceptionHandler(ActivationNotImplementedException.class)
    ResponseEntity<ApiErrorResponse> handleActivationNotImplemented(ActivationNotImplementedException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.NOT_IMPLEMENTED.value(),
                "not_implemented",
                "User activation is not available yet");
    }

    @ExceptionHandler(PasswordValidationException.class)
    ResponseEntity<ApiErrorResponse> handlePasswordValidation(PasswordValidationException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.BAD_REQUEST.value(), "validation_error", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return apiErrorBodyWriter.responseEntity(HttpStatus.FORBIDDEN.value(), "forbidden", "Forbidden");
    }
}
