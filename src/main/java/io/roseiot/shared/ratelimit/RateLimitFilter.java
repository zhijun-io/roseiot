package io.roseiot.shared.ratelimit;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(name = "roseiot.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private final SlidingWindowRateLimiter rateLimiter;
    private final List<RateLimitPolicy> policies;
    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public RateLimitFilter(
            SlidingWindowRateLimiter rateLimiter,
            RateLimitProperties rateLimitProperties,
            JsonMapper jsonMapper,
            ApiErrorBodyWriter apiErrorBodyWriter) {
        this.rateLimiter = rateLimiter;
        this.policies = rateLimitProperties.getRules().stream()
                .<RateLimitPolicy>map(rule -> new JsonBodyFieldRateLimitPolicy(rule, jsonMapper))
                .toList();
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean needsBody = policies.stream().anyMatch(policy -> policy.matches(request));
        if (!needsBody) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        for (RateLimitPolicy policy : policies) {
            if (!policy.matches(request)) {
                continue;
            }
            var key = policy.rateLimitKey(request, body);
            if (key.isEmpty()) {
                continue;
            }
            try {
                rateLimiter.checkAllowed(key.get(), policy.maxAttempts());
            } catch (RateLimitExceededException exception) {
                String code = ApiErrorCode.RATE_LIMITED.code();
                apiErrorBodyWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(), code, code);
                return;
            }
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }
}
