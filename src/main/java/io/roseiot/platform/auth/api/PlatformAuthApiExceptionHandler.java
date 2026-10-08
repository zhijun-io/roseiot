package io.roseiot.platform.auth.api;

import io.roseiot.platform.auth.InvalidCredentialsException;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.api.ApiErrorResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.roseiot.platform.auth.api")
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAuthApiExceptionHandler {

    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public PlatformAuthApiExceptionHandler(ApiErrorBodyWriter apiErrorBodyWriter) {
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.UNAUTHORIZED.value(),
                "invalid_credentials",
                "Invalid email or password");
    }
}
