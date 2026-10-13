package io.roseiot.shared.webmvc;

import io.roseiot.shared.ApiErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies a hard request-body bound independently of route rate limiting. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(RequestBodyProperties.class)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Set<String> METHODS_WITH_BODIES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> AUTHENTICATION_ENDPOINTS = Set.of(
            "/api/login",
            "/api/password-resets",
            "/api/password-resets/validations",
            "/api/password-resets/confirmations");

    private final int maxRequestBodyBytes;
    private final int maxAuthenticationRequestBodyBytes;
    private final ApiHttpSupport apiHttpSupport;

    public RequestBodyLimitFilter(RequestBodyProperties properties, ApiHttpSupport apiHttpSupport) {
        this.maxRequestBodyBytes = properties.maxRequestBodyBytes();
        this.maxAuthenticationRequestBodyBytes = properties.maxAuthenticationRequestBodyBytes();
        this.apiHttpSupport = apiHttpSupport;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        int bodyLimit = AUTHENTICATION_ENDPOINTS.contains(request.getServletPath())
                ? Math.min(maxRequestBodyBytes, maxAuthenticationRequestBodyBytes)
                : maxRequestBodyBytes;
        long contentLength = request.getContentLengthLong();
        if (contentLength > bodyLimit) {
            writeRequestTooLarge(response);
            return;
        }

        boolean mayContainBody =
                contentLength > 0 || (contentLength < 0 && METHODS_WITH_BODIES.contains(request.getMethod()));
        if (!mayContainBody) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(bodyLimit + 1);
        if (body.length > bodyLimit) {
            writeRequestTooLarge(response);
            return;
        }
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private void writeRequestTooLarge(HttpServletResponse response) throws IOException {
        apiHttpSupport.writeError(
                response,
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                ApiErrorCode.REQUEST_TOO_LARGE.code(),
                ApiErrorCode.REQUEST_TOO_LARGE.code());
    }
}
