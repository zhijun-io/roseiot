package io.roseiot.shared.webmvc;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.webmvc.ApiHttpSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Applies a hard request-body bound independently of route rate limiting. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(RequestBodyProperties.class)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Set<String> METHODS_WITH_BODIES = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final int maxRequestBodyBytes;
    private final ApiHttpSupport apiHttpSupport;

    public RequestBodyLimitFilter(RequestBodyProperties properties, ApiHttpSupport apiHttpSupport) {
        this.maxRequestBodyBytes = properties.maxRequestBodyBytes();
        this.apiHttpSupport = apiHttpSupport;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestBodyBytes) {
            writeRequestTooLarge(response);
            return;
        }

        boolean mayContainBody = contentLength > 0
                || (contentLength < 0 && METHODS_WITH_BODIES.contains(request.getMethod()));
        if (!mayContainBody) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxRequestBodyBytes + 1);
        if (body.length > maxRequestBodyBytes) {
            writeRequestTooLarge(response);
            return;
        }
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private void writeRequestTooLarge(HttpServletResponse response) throws IOException {
        apiHttpSupport.writeError(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                ApiErrorCode.REQUEST_TOO_LARGE.code(), ApiErrorCode.REQUEST_TOO_LARGE.code());
    }
}
