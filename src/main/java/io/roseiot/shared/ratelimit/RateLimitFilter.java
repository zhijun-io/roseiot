package io.roseiot.shared.ratelimit;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.webmvc.ApiHttpSupport;
import io.roseiot.shared.webmvc.CachedBodyHttpServletRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

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
        List<RateLimitProperties.Rule> matchingRules =
                rules.stream().filter(rule -> matches(rule, request)).toList();
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
                    ? clientAddressKey(request)
                    : JsonRequestBodyFields.readTextField(body, rule.bodyField(), jsonMapper)
                            .map(value -> value.trim().toLowerCase(Locale.ROOT))
                            .filter(value -> !value.isEmpty())
                            .map(RateLimitFilter::subjectKey)
                            .orElseGet(() -> clientAddressKey(request));
            try {
                rateLimiter.checkAllowed(rule.keyPrefix() + keyPart, rule.maxAttempts());
            } catch (RateLimitCapacityExceededException exception) {
                apiHttpSupport.writeError(
                        response,
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
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
                && PATH_MATCHER.match(rule.path(), request.getServletPath());
    }

    private static String clientAddressKey(HttpServletRequest request) {
        return "client:" + request.getRemoteAddr();
    }

    private static String subjectKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "subject:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void writeRequestTooLarge(HttpServletResponse response) throws IOException {
        apiHttpSupport.writeError(
                response,
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                ApiErrorCode.REQUEST_TOO_LARGE.code(),
                ApiErrorCode.REQUEST_TOO_LARGE.code());
    }
}
