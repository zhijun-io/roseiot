package io.roseiot.shared.ratelimit;

import io.roseiot.shared.webmvc.ApiHttpSupport;
import io.roseiot.shared.webmvc.CachedBodyHttpServletRequest;
import io.roseiot.shared.ApiErrorCode;
import org.springframework.http.HttpStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
@EnableConfigurationProperties(RateLimitProperties.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "roseiot.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SlidingWindowRateLimiter rateLimiter;
    private final List<RateLimitProperties.Rule> rules;
    private final int maxRequestBodyBytes;
    private final JsonMapper jsonMapper;
    private final ApiHttpSupport apiHttpSupport;

    public RateLimitFilter(
            SlidingWindowRateLimiter rateLimiter,
            RateLimitProperties rateLimitProperties,
            JsonMapper jsonMapper,
            ApiHttpSupport apiHttpSupport) {
        this.rateLimiter = rateLimiter;
        this.rules = rateLimitProperties.rules();
        this.maxRequestBodyBytes = rateLimitProperties.maxRequestBodyBytes();
        this.jsonMapper = jsonMapper;
        this.apiHttpSupport = apiHttpSupport;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        List<RateLimitProperties.Rule> matchingRules = rules.stream()
                .filter(rule -> matches(rule, request))
                .toList();
        if (matchingRules.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > maxRequestBodyBytes) {
            writeRequestTooLarge(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxRequestBodyBytes + 1);
        if (body.length > maxRequestBodyBytes) {
            writeRequestTooLarge(response);
            return;
        }
        for (RateLimitProperties.Rule rule : matchingRules) {
            String keyPart = rule.bodyField() == null
                    ? request.getRemoteAddr()
                    : JsonRequestBodyFields.readTextField(body, rule.bodyField(), jsonMapper)
                            .map(value -> value.trim().toLowerCase(Locale.ROOT))
                            .filter(value -> !value.isEmpty())
                            .orElse(null);
            if (keyPart == null || keyPart.isBlank()) {
                continue;
            }
            try {
                rateLimiter.checkAllowed(rule.keyPrefix() + keyPart, rule.maxAttempts());
            } catch (RateLimitCapacityExceededException exception) {
                apiHttpSupport.writeError(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                        ApiErrorCode.RATE_LIMIT_CAPACITY_EXHAUSTED.code(),
                        "Rate limiting capacity is temporarily unavailable");
                return;
            } catch (RateLimitExceededException exception) {
                String code = ApiErrorCode.RATE_LIMITED.code();
                response.setHeader("Retry-After", Long.toString(exception.retryAfterSeconds()));
                apiHttpSupport.writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(), code, code);
                return;
            }
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private static boolean matches(RateLimitProperties.Rule rule, HttpServletRequest request) {
        return rule.method().equalsIgnoreCase(request.getMethod())
                && PATH_MATCHER.match(rule.path(), request.getRequestURI());
    }

    private void writeRequestTooLarge(HttpServletResponse response) throws IOException {
        apiHttpSupport.writeError(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                ApiErrorCode.REQUEST_TOO_LARGE.code(), ApiErrorCode.REQUEST_TOO_LARGE.code());
    }
}
