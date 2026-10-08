package io.roseiot.platform.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(PlatformAuthRateLimitProperties.class)
public class PlatformAuthRateLimitConfig {}
