package io.roseiot.shared.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "roseiot.rate-limit")
public record RateLimitProperties(
        @Min(1) int windowMinutes,
        @Min(1) int maxRequestBodyBytes,
        @Min(1) int maxTrackedKeys,
        @NotNull List<@Valid Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? null : List.copyOf(rules);
    }

    public record Rule(
            @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
            String method,

            @NotBlank String path,
            String bodyField,
            @NotBlank String keyPrefix,
            @Min(1) int maxAttempts) {}
}
