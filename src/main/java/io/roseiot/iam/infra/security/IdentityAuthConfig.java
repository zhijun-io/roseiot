package io.roseiot.iam.infra.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.roseiot.iam.infra.passwordreset.LoggingPasswordResetNotifier;
import io.roseiot.iam.infra.passwordreset.PasswordResetNotifier;
import io.roseiot.system.application.SystemSettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class IdentityAuthConfig {

    @Bean
    JwtDecoder identityJwtDecoder(@Value("${roseiot.auth.jwt.secret}") String hmacSecret) {
        return NimbusJwtDecoder.withSecretKey(hmacSecretKey(hmacSecret)).build();
    }

    @Bean
    JwtEncoder identityJwtEncoder(@Value("${roseiot.auth.jwt.secret}") String hmacSecret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacSecretKey(hmacSecret)));
    }

    @Bean
    @Profile("local")
    @ConditionalOnMissingBean(PasswordResetNotifier.class)
    PasswordResetNotifier passwordResetNotifier(SystemSettingService settings) {
        return new LoggingPasswordResetNotifier(settings);
    }

    private static SecretKey hmacSecretKey(String hmacSecret) {
        byte[] secretBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("roseiot.auth.jwt.secret must be at least 256 bits");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
