package io.roseiot.iam.infra.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.persistence.User;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenFactoryTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final String SECRET = "test-secret-with-a-full-256-bit-minimum-value";

    private JwtTokenFactory factory;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).build();
        factory = new JwtTokenFactory(encoder, decoder);
    }

    @Test
    void accessTokenKeepsExistingIdentityClaimsAndFifteenMinuteLifetime() {
        Instant now = Instant.now();
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setAuthorizationVersion(7);

        var jwt = decoder.decode(factory.createAccessToken(user, SESSION_ID, now));

        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString(JwtTokenFactory.SESSION_ID)).isEqualTo(SESSION_ID.toString());
        assertThat(jwt.getClaimAsString(JwtTokenFactory.TENANT_ID)).isEqualTo(TENANT_ID.toString());
        assertThat(((Number) jwt.getClaim(JwtTokenFactory.AUTHORIZATION_VERSION)).longValue()).isEqualTo(7L);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.truncatedTo(ChronoUnit.SECONDS).plus(15, ChronoUnit.MINUTES));
    }

    @Test
    void refreshTokenIsSignedHasTypeAndSevenDayLifetime() {
        Instant now = Instant.now();

        String token = factory.createRefreshToken(USER_ID, SESSION_ID, FAMILY_ID, now);
        JwtTokenFactory.RefreshClaims claims = factory.decodeRefreshToken(token);
        var jwt = decoder.decode(token);

        assertThat(claims).isEqualTo(new JwtTokenFactory.RefreshClaims(USER_ID, SESSION_ID, FAMILY_ID));
        assertThat(jwt.getClaimAsString(JwtTokenFactory.TOKEN_TYPE)).isEqualTo(JwtTokenFactory.REFRESH_TOKEN);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.truncatedTo(ChronoUnit.SECONDS).plus(7, ChronoUnit.DAYS));
    }

    @Test
    void rejectsAccessTokenWhereRefreshTokenIsExpected() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setAuthorizationVersion(1);
        String accessToken = factory.createAccessToken(user, SESSION_ID, Instant.now());

        assertThatThrownBy(() -> factory.decodeRefreshToken(accessToken))
                .isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
    }

    @Test
    void rejectsBadSignatureAndExpiredRefreshToken() {
        String valid = factory.createRefreshToken(USER_ID, SESSION_ID, FAMILY_ID, Instant.now());
        String forged = valid.substring(0, valid.length() - 1) + (valid.endsWith("a") ? "b" : "a");
        String expired = factory.createRefreshToken(
                USER_ID, SESSION_ID, FAMILY_ID, Instant.now().minus(8, ChronoUnit.DAYS));

        assertThatThrownBy(() -> factory.decodeRefreshToken(forged))
                .isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
        assertThatThrownBy(() -> factory.decodeRefreshToken(expired))
                .isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
    }
}
