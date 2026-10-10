package io.roseiot.identity.infra.security;

import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.infra.security.session.RestLoginAuthenticationFilter;
import io.roseiot.identity.infra.security.session.RefreshTokenAuthenticationFilter;
import io.roseiot.identity.infra.security.session.SessionLogoutFilter;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
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
    RestLoginAuthenticationFilter jsonLoginAuthenticationFilter(
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
    SessionLogoutFilter sessionLogoutFilter(UserSessionService sessionService) {
        return new SessionLogoutFilter(sessionService);
    }

    /**
     * Public chain: no JWT parsing so stale {@code Authorization} headers cannot turn login/refresh into 401.
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
                        "/api/login",
                        "/api/token",
                        "/api/noauth/password-reset/email",
                        "/api/noauth/password-reset",
                        "/api/noauth/user-activation",
                        "/api/noauth/tenant-invitations")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/login", "/api/token")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/noauth/password-reset/email",
                                "/api/noauth/password-reset", "/api/noauth/user-activation",
                                "/api/noauth/tenant-invitations")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/noauth/password-reset",
                                "/api/noauth/user-activation", "/api/noauth/tenant-invitations")
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
            SessionLogoutFilter sessionLogoutFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/me/**")
                        .authenticated()
                        .requestMatchers("/api/tenants/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/logout", "/api/logout-all")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(identityJwtAuthenticationConverter)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .addFilterAfter(sessionLogoutFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
