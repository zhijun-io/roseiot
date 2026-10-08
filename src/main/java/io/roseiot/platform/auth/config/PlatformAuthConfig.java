package io.roseiot.platform.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.roseiot.platform.auth.infra.recovery.LoggingRecoveryNotifier;
import io.roseiot.platform.auth.infra.recovery.RecoveryNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(PlatformAuthRateLimitProperties.class)
public class PlatformAuthConfig {

    @Bean
    JwtDecoder platformAdminJwtDecoder(@Value("${roseiot.auth.jwt.hmac-secret}") String hmacSecret) {
        return NimbusJwtDecoder.withSecretKey(hmacSecretKey(hmacSecret)).build();
    }

    @Bean
    JwtEncoder platformAdminJwtEncoder(@Value("${roseiot.auth.jwt.hmac-secret}") String hmacSecret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacSecretKey(hmacSecret)));
    }

    @Bean
    @ConditionalOnMissingBean(RecoveryNotifier.class)
    RecoveryNotifier loggingRecoveryNotifier() {
        return new LoggingRecoveryNotifier();
    }

    private static SecretKey hmacSecretKey(String hmacSecret) {
        byte[] secretBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("roseiot.auth.jwt.hmac-secret must be at least 256 bits");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
