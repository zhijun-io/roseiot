package io.roseiot.iam.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.roseiot.shared.security.JwtTokenFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenFactoryTest {
    private static final Long USER_ID = System.nanoTime();
    private static final Long TENANT_ID = 42L;
    private JwtTokenFactory factory;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec("test-secret-with-a-full-256-bit-minimum-value".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).build();
        factory = new JwtTokenFactory(encoder);
    }

    @Test
    void createsOnlyShortLivedAccessTokenWithUserAndTenantClaims() {
        Instant now = Instant.now();
        var jwt = decoder.decode(factory.createAccessToken(USER_ID, TENANT_ID, now));
        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString(JwtTokenFactory.TENANT_ID)).isEqualTo(TENANT_ID.toString());
        assertThat(jwt.getClaims()).doesNotContainKeys("sid", "type", "family");
        assertThat(jwt.getExpiresAt()).isEqualTo(now.truncatedTo(ChronoUnit.SECONDS).plus(15, ChronoUnit.MINUTES));
    }
}
