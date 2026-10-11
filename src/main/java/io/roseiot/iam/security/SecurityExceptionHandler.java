package io.roseiot.iam.security;

import io.roseiot.shared.webmvc.ApiJson;
import io.roseiot.shared.ApiErrorResponse;
import io.roseiot.shared.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/** JSON 401/403：Servlet 过滤器链与 MVC {@code @ExceptionHandler} 共用同一映射。 */
@RestControllerAdvice(basePackages = "io.roseiot")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiJson apiJson;

    public SecurityExceptionHandler(ApiJson apiJson) {
        this.apiJson = apiJson;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        String code = authenticationErrorCode(authException);
        apiJson.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, code, code);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        apiJson.writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden");
    }

    public void handleMalformedRequest(HttpServletResponse response) throws IOException {
        apiJson.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "bad_request", "Malformed request body");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception) {
        String code = authenticationErrorCode(exception);
        return apiJson.errorResponse(HttpStatus.UNAUTHORIZED.value(), code, code);
    }

    private static String authenticationErrorCode(AuthenticationException exception) {
        return exception instanceof BadCredentialsException
                ? ApiErrorCode.INVALID_CREDENTIALS.code()
                : "unauthorized";
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return apiJson.errorResponse(HttpStatus.FORBIDDEN.value(), "forbidden", "Forbidden");
    }

}
