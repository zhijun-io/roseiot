package io.roseiot.shared.webmvc;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "roseiot.http")
public record RequestBodyProperties(
        @Min(1) int maxRequestBodyBytes, @Min(1) int maxAuthenticationRequestBodyBytes) {}
