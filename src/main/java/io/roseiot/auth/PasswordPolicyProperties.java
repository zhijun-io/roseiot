package io.roseiot.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.password-policy")
record PasswordPolicyProperties(@Min(8) @Max(128) int minimumLength) {}
