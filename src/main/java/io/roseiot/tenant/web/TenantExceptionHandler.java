package io.roseiot.tenant.web;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.api.ApiErrorResponse;
import io.roseiot.tenant.domain.TenantNotFoundException;
import io.roseiot.tenant.domain.TenantSlugConflictException;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.roseiot.tenant.web")
@Order(0)
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class TenantExceptionHandler {

    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public TenantExceptionHandler(ApiErrorBodyWriter apiErrorBodyWriter) {
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @ExceptionHandler(TenantNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleTenantNotFound(TenantNotFoundException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.NOT_FOUND.value(), "tenant_not_found", "Tenant not found");
    }

    @ExceptionHandler(TenantSlugConflictException.class)
    ResponseEntity<ApiErrorResponse> handleSlugConflict(TenantSlugConflictException exception) {
        return apiErrorBodyWriter.responseEntity(
                HttpStatus.CONFLICT.value(), "tenant_slug_conflict", "Tenant slug already in use");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return apiErrorBodyWriter.responseEntity(HttpStatus.FORBIDDEN.value(), "forbidden", "Forbidden");
    }
}
