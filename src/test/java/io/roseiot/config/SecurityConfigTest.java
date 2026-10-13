package io.roseiot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class SecurityConfigTest {

    private static final String VALID_SECRET = "test-only-secret-with-at-least-256-bits-long";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withPropertyValues(
                    "roseiot.auth.jwt.issuer=https://roseiot.local", "roseiot.auth.jwt.audience=roseiot-api")
            .withBean(UserDetailsService.class, () -> mock(UserDetailsService.class));

    @Test
    void shouldFailWhenJwtSecretIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("roseiot.auth.jwt.secret");
        });
    }

    @Test
    void jwtPropertiesShouldNotExposeItsSecretInToString() {
        assertThat(new JwtProperties(VALID_SECRET, "https://roseiot.local", "roseiot-api").toString())
                .doesNotContain(VALID_SECRET);
    }

    @Test
    void shouldFailWhenJwtSecretIsShorterThan256Bits() {
        contextRunner.withPropertyValues("roseiot.auth.jwt.secret=too-short").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("must be at least 256 bits");
        });
    }

    @Test
    void shouldFailWhenJwtIssuerIsNotAnAbsoluteHttpUrl() {
        contextRunner
                .withPropertyValues("roseiot.auth.jwt.secret=" + VALID_SECRET, "roseiot.auth.jwt.issuer=roseiot")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("issuer must be an absolute HTTP(S) URL");
                });
    }

    @Test
    void shouldProvideJwtAndPasswordServicesWithAValidSecret() {
        contextRunner
                .withPropertyValues("roseiot.auth.jwt.secret=" + VALID_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(org.springframework.security.crypto.password.PasswordEncoder.class);
                    assertThat(context).hasSingleBean(org.springframework.security.oauth2.jwt.JwtEncoder.class);
                    assertThat(context).hasSingleBean(org.springframework.security.oauth2.jwt.JwtDecoder.class);
                });
    }

    @Test
    void jwtDecoderRequiresConfiguredIssuerAndAudience() {
        contextRunner
                .withPropertyValues("roseiot.auth.jwt.secret=" + VALID_SECRET)
                .run(context -> {
                    JwtEncoder encoder = context.getBean(JwtEncoder.class);
                    JwtDecoder decoder = context.getBean(JwtDecoder.class);
                    Instant now = Instant.now();

                    String validToken = token(encoder, "https://roseiot.local", "roseiot-api", now);
                    assertThat(decoder.decode(validToken).getSubject()).isEqualTo("1001");

                    assertThatThrownBy(
                                    () -> decoder.decode(token(encoder, "https://other.example", "roseiot-api", now)))
                            .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
                    assertThatThrownBy(() -> decoder.decode(token(encoder, "https://roseiot.local", "other-api", now)))
                            .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
                });
    }

    private static String token(JwtEncoder encoder, String issuer, String audience, Instant now) {
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .issuer(issuer)
                                .subject("1001")
                                .audience(List.of(audience))
                                .issuedAt(now)
                                .expiresAt(now.plusSeconds(60))
                                .build()))
                .getTokenValue();
    }

    @Test
    void passwordEncoderShouldWriteDelegatingHashesAndReadLegacyBcryptHashes() {
        PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();
        String rawPassword = "test-password-1234";
        String legacyHash = new BCryptPasswordEncoder().encode(rawPassword);

        assertThat(passwordEncoder.matches(rawPassword, legacyHash)).isTrue();

        String currentHash = passwordEncoder.encode(rawPassword);
        assertThat(currentHash).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(rawPassword, currentHash)).isTrue();
    }
}
