package io.roseiot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private static final String VALID_SECRET = "test-only-secret-with-at-least-256-bits-long";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withBean(UserDetailsService.class, () -> mock(UserDetailsService.class));

    @Test
    void shouldFailWhenJwtSecretIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("roseiot.auth.jwt.secret");
        });
    }

    @Test
    void shouldFailWhenJwtSecretIsShorterThan256Bits() {
        contextRunner.withPropertyValues("roseiot.auth.jwt.secret=too-short").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("must be at least 256 bits");
        });
    }

    @Test
    void shouldProvideJwtAndPasswordServicesWithAValidSecret() {
        contextRunner.withPropertyValues("roseiot.auth.jwt.secret=" + VALID_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(org.springframework.security.crypto.password.PasswordEncoder.class);
            assertThat(context).hasSingleBean(org.springframework.security.oauth2.jwt.JwtEncoder.class);
            assertThat(context).hasSingleBean(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        });
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
