package io.roseiot.iam.infra.security;

import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.infra.security.session.RefreshTokenAuthenticationFilter;
import io.roseiot.iam.infra.security.session.RestLoginAuthenticationFilter;
import io.roseiot.iam.infra.security.session.RestLogoutFilter;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean(name = "identityAuthenticationManager")
    AuthenticationManager identityAuthenticationManager(
            SecurityUserDetailsService userDetailsService,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    RestLoginAuthenticationFilter restLoginAuthenticationFilter(
            @Qualifier("identityAuthenticationManager") AuthenticationManager authenticationManager,
            UserSessionService sessionService,
            SecurityExceptionHandler securityExceptionHandler,
            JsonMapper jsonMapper,
            ApiErrorBodyWriter apiErrorBodyWriter) {
        return new RestLoginAuthenticationFilter(
                authenticationManager, sessionService, securityExceptionHandler, jsonMapper, apiErrorBodyWriter);
    }

    @Bean
    RefreshTokenAuthenticationFilter refreshTokenAuthenticationFilter(
            UserSessionService sessionService,
            SecurityExceptionHandler securityExceptionHandler,
            JsonMapper jsonMapper,
            ApiErrorBodyWriter apiErrorBodyWriter) {
        return new RefreshTokenAuthenticationFilter(
                sessionService, securityExceptionHandler, jsonMapper, apiErrorBodyWriter);
    }

    @Bean
    RestLogoutFilter restLogoutFilter(UserSessionService sessionService) {
        return new RestLogoutFilter(sessionService);
    }

    @Bean
    FilterRegistrationBean<RestLoginAuthenticationFilter> restLoginFilterRegistration(
            RestLoginAuthenticationFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<RefreshTokenAuthenticationFilter> refreshFilterRegistration(
            RefreshTokenAuthenticationFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<RestLogoutFilter> restLogoutFilterRegistration(RestLogoutFilter filter) {
        return disabledServletRegistration(filter);
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledServletRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Public namespace: no access-token parsing so login, refresh and other noauth requests remain reachable.
     */
    @Bean
    @Order(1)
    SecurityFilterChain publicSecurityFilterChain(
            HttpSecurity http,
            RestLoginAuthenticationFilter restLoginAuthenticationFilter,
            RefreshTokenAuthenticationFilter refreshTokenAuthenticationFilter)
            throws Exception {
        http.securityMatcher(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/login",
                        "/token",
                        "/noauth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/login", "/token")
                        .permitAll()
                        .requestMatchers("/noauth/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .addFilterBefore(restLoginAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(refreshTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain authenticatedSecurityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler,
            IdentityJwtAuthenticationConverter identityJwtAuthenticationConverter,
            RestLogoutFilter restLogoutFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/logout")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(identityJwtAuthenticationConverter)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .addFilterAfter(restLogoutFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
