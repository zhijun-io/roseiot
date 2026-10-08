package io.roseiot.platform.auth.web;

import io.roseiot.AbstractIt;
import io.roseiot.platform.bootstrap.BootstrapPlatformAdminService;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRecoveryRequestMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@SpringBootTest(
        properties = {
            "roseiot.auth.rate-limit.enabled=true",
            "roseiot.auth.rate-limit.login-max-attempts=2",
            "roseiot.auth.rate-limit.recovery-max-attempts=2",
            "roseiot.auth.rate-limit.window-minutes=15"
        })
class PlatformAdminAuthRateLimitMockMvcTests extends AbstractIt {

    @Autowired
    BootstrapPlatformAdminService bootstrapPlatformAdminService;

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Autowired
    PlatformAdminRefreshSessionMapper refreshSessionMapper;

    @Autowired
    PlatformAdminRecoveryRequestMapper recoveryRequestMapper;

    @BeforeEach
    void seedPlatformAdmin() {
        recoveryRequestMapper.delete(null);
        refreshSessionMapper.delete(null);
        platformAdminMapper.delete(null);
        bootstrapPlatformAdminService.bootstrap("admin@example.com", "password1234");
    }

    @Test
    void loginRateLimitReturns429WithStableBody() {
        postLogin("admin@example.com", "wrong1").assertThat().hasStatus(HttpStatus.UNAUTHORIZED);
        postLogin("admin@example.com", "wrong2").assertThat().hasStatus(HttpStatus.UNAUTHORIZED);

        postLogin("admin@example.com", "wrong3")
                .assertThat()
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("rate_limited");
    }

    @Test
    void recoveryRequestRateLimitReturns429WithStableBody() {
        postRecovery("admin@example.com").assertThat().hasStatus(HttpStatus.ACCEPTED);
        postRecovery("admin@example.com").assertThat().hasStatus(HttpStatus.ACCEPTED);

        postRecovery("admin@example.com")
                .assertThat()
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("rate_limited");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postLogin(String email, String password) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                                .formatted(email, password))
                .exchange();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postRecovery(String email) {
        return mvc.post()
                .uri("/api/auth/recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"email":"%s"}
                        """
                                .formatted(email))
                .exchange();
    }
}
