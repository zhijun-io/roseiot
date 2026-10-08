package io.roseiot.identity.web;

import io.roseiot.AbstractIt;
import io.roseiot.identity.application.BootstrapUserService;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRecoveryRequestMapper;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@SpringBootTest(
        properties = {
            "roseiot.rate-limit.enabled=true",
            "roseiot.rate-limit.window-minutes=15",
            "roseiot.rate-limit.rules[0].method=POST",
            "roseiot.rate-limit.rules[0].path=/api/identity/auth/login",
            "roseiot.rate-limit.rules[0].body-field=email",
            "roseiot.rate-limit.rules[0].key-prefix=identity:auth:login:",
            "roseiot.rate-limit.rules[0].max-attempts=2",
            "roseiot.rate-limit.rules[1].method=POST",
            "roseiot.rate-limit.rules[1].path=/api/identity/auth/recovery/request",
            "roseiot.rate-limit.rules[1].body-field=email",
            "roseiot.rate-limit.rules[1].key-prefix=identity:auth:recovery:",
            "roseiot.rate-limit.rules[1].max-attempts=2"
        })
class IdentityAuthRateLimitMockMvcTests extends AbstractIt {

    @Autowired
    BootstrapUserService bootstrapUserService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserRefreshSessionMapper refreshSessionMapper;

    @Autowired
    UserRecoveryRequestMapper recoveryRequestMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedPlatformUser() {
        recoveryRequestMapper.delete(null);
        refreshSessionMapper.delete(null);
        jdbcTemplate.update("DELETE FROM user_role");
        userMapper.delete(null);
        bootstrapUserService.bootstrap("admin@example.com", "password1234");
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
                .uri("/api/identity/auth/login")
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
                .uri("/api/identity/auth/recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"email":"%s"}
                        """
                                .formatted(email))
                .exchange();
    }
}
