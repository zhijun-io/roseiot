package io.roseiot.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 限速规则契约；实例由 {@link RateLimitFilter} 根据 {@link RateLimitProperties#getRules()} 装配，在请求进入业务前执行。
 */
public interface RateLimitPolicy {

    boolean matches(HttpServletRequest request);

    Optional<String> rateLimitKey(HttpServletRequest request, byte[] requestBody);

    int maxAttempts();
}
