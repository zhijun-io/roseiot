package io.roseiot.shared.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.shared.ratelimit.RateLimitExceededException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.roseiot")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ApiJson apiJson;

    public ApiExceptionHandler(ApiJson apiJson) {
        this.apiJson = apiJson;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException exception) {
        String code = ApiErrorCode.RATE_LIMITED.code();
        return apiJson.errorResponse(HttpStatus.TOO_MANY_REQUESTS.value(), code, code);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception) {
        ApiErrorCode code = exception.errorCode();
        return apiJson.errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), code.code(), exception.apiMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        return apiJson.errorResponse(
                HttpStatus.BAD_REQUEST.value(), "validation_error", "Validation failed");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return apiJson.errorResponse(
                HttpStatus.BAD_REQUEST.value(), "bad_request", "Malformed request body");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled API exception", exception);
        return apiJson.errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "internal_error",
                "An unexpected error occurred");
    }
}
