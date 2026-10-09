package io.roseiot.identity.infra.security;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/** JSON 401/403：Servlet 过滤器链与 MVC {@code @ExceptionHandler} 共用同一映射。 */
@RestControllerAdvice(basePackages = "io.roseiot")
public class JsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public JsonSecurityHandlers(ApiErrorBodyWriter apiErrorBodyWriter) {
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        writeAuthenticationFailure(response, authException);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        writeAccessDenied(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception) {
        AuthenticationExceptionMapping.Mapped mapped = AuthenticationExceptionMapping.map(exception);
        return apiErrorBodyWriter.responseEntity(mapped.httpStatus(), mapped.code(), mapped.message());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return apiErrorBodyWriter.responseEntity(HttpStatus.FORBIDDEN.value(), "forbidden", "Forbidden");
    }

    private void writeAuthenticationFailure(HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        AuthenticationExceptionMapping.Mapped mapped = AuthenticationExceptionMapping.map(exception);
        apiErrorBodyWriter.write(response, mapped.httpStatus(), mapped.code(), mapped.message());
    }

    private void writeAccessDenied(HttpServletResponse response) throws IOException {
        apiErrorBodyWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden");
    }
}
