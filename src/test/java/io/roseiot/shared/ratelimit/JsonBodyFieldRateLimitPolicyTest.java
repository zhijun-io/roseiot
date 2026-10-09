package io.roseiot.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonBodyFieldRateLimitPolicyTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private RateLimitProperties.Rule loginRule() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setMethod("POST");
        rule.setPath("/api/login");
        rule.setBodyField("email");
        rule.setKeyPrefix("auth:login:");
        rule.setMaxAttempts(5);
        return rule;
    }

    private HttpServletRequest request(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    private byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shouldMatchConfiguredMethodAndPath() {
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(loginRule(), jsonMapper);

        assertThat(cut.matches(request("POST", "/api/login"))).isTrue();
    }

    @Test
    void shouldMatchMethodIgnoringCaseBecauseClientsSendBoth() {
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(loginRule(), jsonMapper);

        assertThat(cut.matches(request("post", "/api/login"))).isTrue();
    }

    @Test
    void shouldNotMatchAnotherPath() {
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(loginRule(), jsonMapper);

        assertThat(cut.matches(request("POST", "/api/noauth/password-reset/email"))).isFalse();
    }

    @Test
    void shouldNotMatchAnotherMethod() {
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(loginRule(), jsonMapper);

        assertThat(cut.matches(request("GET", "/api/login"))).isFalse();
    }

    @Test
    void shouldPrefixNormalizedBodyFieldValueAsKey() {
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(loginRule(), jsonMapper);
        byte[] body = body("""
                {"email":"  Ops@RoseIoT.io  "}
                """);

        Optional<String> key = cut.rateLimitKey(request("POST", "/api/login"), body);

        assertThat(key).contains("auth:login:ops@roseiot.io");
    }

    @Test
    void shouldKeepRawValueWhenNormalizationIsOff() {
        RateLimitProperties.Rule rule = loginRule();
        rule.setNormalizeBodyField(false);
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(rule, jsonMapper);
        byte[] body = body("""
                {"email":"  Ops@RoseIoT.io  "}
                """);

        assertThat(cut.rateLimitKey(request("POST", "/api/login"), body))
                .contains("auth:login:  Ops@RoseIoT.io  ");
    }

    @Test
    void shouldProduceNoKeyWhenFieldIsMissing() {
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(loginRule(), jsonMapper);

        assertThat(cut.rateLimitKey(request("POST", "/api/login"), body("{}"))).isEmpty();
    }

    @Test
    void shouldProduceNoKeyWhenNormalizationEmptiesTheValue() {
        RateLimitProperties.Rule rule = loginRule();
        rule.setBodyField("tenant");
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(rule, jsonMapper);

        // "  " is filtered by the reader; a value that normalizes to empty must not become "auth:login:".
        assertThat(cut.rateLimitKey(request("POST", "/api/login"),
                body("""
                        {"tenant":"   "}
                        """))).isEmpty();
    }

    @Test
    void shouldExposeConfiguredAttemptLimit() {
        JsonBodyFieldRateLimitPolicy cut = new JsonBodyFieldRateLimitPolicy(loginRule(), jsonMapper);

        assertThat(cut.maxAttempts()).isEqualTo(5);
    }
}
