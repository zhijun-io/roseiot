package io.roseiot.shared.security;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.webmvc.ApiHttpSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** JSON 401/403：Servlet 过滤器链与 MVC {@code @ExceptionHandler} 共用同一映射。 */
@RestControllerAdvice(basePackages = "io.roseiot")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiHttpSupport apiHttpSupport;

    public SecurityExceptionHandler(ApiHttpSupport apiHttpSupport) {
        this.apiHttpSupport = apiHttpSupport;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String code = authenticationErrorCode(authException);
        apiHttpSupport.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, code, code);
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        apiHttpSupport.writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden");
    }

    public void handleMalformedRequest(HttpServletResponse response) throws IOException {
        apiHttpSupport.writeError(
                response, HttpServletResponse.SC_BAD_REQUEST, "bad_request", "Malformed request body");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception) {
        String code = authenticationErrorCode(exception);
        return apiHttpSupport.errorResponse(HttpStatus.UNAUTHORIZED.value(), code, code);
    }

    private static String authenticationErrorCode(AuthenticationException exception) {
        if (exception instanceof BadCredentialsException) {
            return ApiErrorCode.INVALID_CREDENTIALS.code();
        }
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && "invalid_token".equals(oauth2Exception.getError().getErrorCode())) {
            return "invalid_token";
        }
        return "unauthorized";
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        return apiHttpSupport.errorResponse(HttpStatus.FORBIDDEN.value(), "forbidden", "Forbidden");
    }
}
