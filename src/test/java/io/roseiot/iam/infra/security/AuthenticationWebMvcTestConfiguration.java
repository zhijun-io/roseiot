package io.roseiot.iam.infra.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import static org.mockito.Mockito.mock;


/** Lets {@code @WebMvcTest} slices load the login filter without full identity wiring. */
@TestConfiguration
class AuthenticationWebMvcTestConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "identityAuthenticationManager")
    AuthenticationManager identityAuthenticationManager() {
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
