package io.roseiot.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Optional;

final class JsonBodyFieldRateLimitPolicy implements RateLimitPolicy {

    private final RateLimitProperties.Rule rule;
    private final JsonMapper jsonMapper;

    JsonBodyFieldRateLimitPolicy(RateLimitProperties.Rule rule, JsonMapper jsonMapper) {
        this.rule = rule;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return rule.getMethod().equalsIgnoreCase(request.getMethod())
                && rule.getPath().equals(request.getRequestURI());
    }

    @Override
    public Optional<String> rateLimitKey(HttpServletRequest request, byte[] requestBody) {
        return JsonRequestBodyFields.readTextField(requestBody, rule.getBodyField(), jsonMapper)
                .map(this::normalizeIfConfigured)
                .filter(value -> !value.isEmpty())
                .map(value -> rule.getKeyPrefix() + value);
    }

    @Override
    public int maxAttempts() {
        return rule.getMaxAttempts();
    }

    private String normalizeIfConfigured(String raw) {
        if (!rule.isNormalizeBodyField()) {
            return raw;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
