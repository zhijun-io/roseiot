package io.roseiot.config;

import io.roseiot.shared.security.JwtAuthenticationConverter;
import io.roseiot.shared.security.JwtTokenFactory;
import io.roseiot.shared.security.RestLoginAuthenticationFilter;
import io.roseiot.shared.security.SecurityExceptionHandler;
import io.roseiot.shared.webmvc.ApiHttpSupport;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Bean
    RestLoginAuthenticationFilter restLoginAuthenticationFilter(
            AuthenticationManager authenticationManager,
            JwtTokenFactory jwtTokenFactory,
            SecurityExceptionHandler securityExceptionHandler,
            ApiHttpSupport apiHttpSupport,
            Clock clock) {
        return new RestLoginAuthenticationFilter(
                authenticationManager, jwtTokenFactory, securityExceptionHandler, apiHttpSupport, clock);
    }

    @Bean
    FilterRegistrationBean<RestLoginAuthenticationFilter> restLoginFilterRegistration(
            RestLoginAuthenticationFilter filter) {
        FilterRegistrationBean<RestLoginAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Public resources do not require an access token.
     */
    @Bean
    @Order(1)
    SecurityFilterChain publicSecurityFilterChain(
            HttpSecurity http, RestLoginAuthenticationFilter restLoginAuthenticationFilter) throws Exception {
        http.securityMatcher("/api/actuator/health", "/api/actuator/health/**", "/api/login", "/api/password-resets/**")
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers(HttpMethod.GET, "/api/actuator/health", "/api/actuator/health/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/login")
                                .permitAll()
                                .requestMatchers("/api/password-resets/**")
                                .permitAll()
                                .anyRequest()
                                .denyAll())
                .addFilterBefore(restLoginAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain authenticatedSecurityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http.securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler));
        return http.build();
    }
}
