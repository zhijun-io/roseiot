package io.roseiot.iam.security;

import static org.mockito.Mockito.mock;

import io.roseiot.shared.security.JwtTokenFactory;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

/** Lets {@code @WebMvcTest} slices load the login filter without full identity wiring. */
@TestConfiguration
@EnableSpringDataWebSupport
class AuthenticationWebMvcTestConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new BadCredentialsException("stub");
        };
    }

    @Bean
    @ConditionalOnMissingBean(JwtTokenFactory.class)
    JwtTokenFactory jwtTokenFactory() {
        return mock(JwtTokenFactory.class);
    }
}
