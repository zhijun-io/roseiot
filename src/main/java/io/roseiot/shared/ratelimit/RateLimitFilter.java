package io.roseiot.shared.ratelimit;

import io.roseiot.shared.api.ApiJson;
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
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "roseiot.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private final SlidingWindowRateLimiter rateLimiter;
    private final List<RateLimitProperties.Rule> rules;
    private final JsonMapper jsonMapper;
    private final ApiJson apiJson;

    public RateLimitFilter(
            SlidingWindowRateLimiter rateLimiter,
            RateLimitProperties rateLimitProperties,
            JsonMapper jsonMapper,
            ApiJson apiJson) {
        this.rateLimiter = rateLimiter;
        this.rules = rateLimitProperties.getRules();
        this.jsonMapper = jsonMapper;
        this.apiJson = apiJson;
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

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        for (RateLimitProperties.Rule rule : matchingRules) {
            var key = JsonRequestBodyFields.readTextField(body, rule.getBodyField(), jsonMapper)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isEmpty())
                    .map(value -> rule.getKeyPrefix() + value);
            if (key.isEmpty()) {
                continue;
            }
            try {
                rateLimiter.checkAllowed(key.get(), rule.getMaxAttempts());
            } catch (RateLimitExceededException exception) {
                String code = ApiErrorCode.RATE_LIMITED.code();
                apiJson.writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(), code, code);
                return;
            }
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private static boolean matches(RateLimitProperties.Rule rule, HttpServletRequest request) {
        return rule.getMethod().equalsIgnoreCase(request.getMethod())
                && rule.getPath().equals(request.getRequestURI());
    }
}
