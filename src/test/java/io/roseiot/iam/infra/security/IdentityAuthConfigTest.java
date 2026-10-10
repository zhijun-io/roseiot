package io.roseiot.iam.infra.security;

import io.roseiot.iam.infra.passwordreset.PasswordResetNotifier;
import io.roseiot.system.application.SystemSettingService;
import io.roseiot.tenant.config.TenantConfig;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdentityAuthConfigTest {

    private static final String VALID_SECRET = "test-only-secret-with-at-least-256-bits-long";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IdentityAuthConfig.class, TenantConfig.class)
            .withBean(SystemSettingService.class, () -> mock(SystemSettingService.class))
            .withPropertyValues("roseiot.auth.tenant-invitation.link-base-url=http://localhost:8080");

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
    void shouldNotRegisterLoggingNotifiersOutsideTheLocalProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=prod",
                        "roseiot.auth.jwt.secret=" + VALID_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PasswordResetNotifier.class)).isEmpty();
                    assertThat(context.getBeansOfType(TenantInvitationNotifier.class)).isEmpty();
                });
    }

    @Test
    void shouldKeepManualLoggingNotifiersInTheExplicitLocalProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=local",
                        "roseiot.auth.jwt.secret=" + VALID_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PasswordResetNotifier.class)).hasSize(1);
                    assertThat(context.getBeansOfType(TenantInvitationNotifier.class)).hasSize(1);
                });
    }
}
