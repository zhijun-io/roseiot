package io.roseiot.iam.infra.security;

import io.roseiot.iam.application.UserSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import static org.mockito.Mockito.mock;

/** Lets {@code @WebMvcTest} slices load session filters without full identity application wiring. */
@TestConfiguration
class SessionAuthWebMvcTestConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "identityAuthenticationManager")
    AuthenticationManager identityAuthenticationManager() {
        return authentication -> {
            throw new BadCredentialsException("stub");
        };
    }

    @Bean
    @ConditionalOnMissingBean(UserSessionService.class)
    UserSessionService userSessionService() {
        return mock(UserSessionService.class);
    }
}
