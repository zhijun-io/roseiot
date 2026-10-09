package io.roseiot.identity.infra.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class SecurityConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.addFirst(new SecurityUserArgumentResolver());
    }

    /**
     * Anonymous auth flows without JWT parsing so stale/invalid {@code Authorization} headers cannot
     * turn a public endpoint into 401.
     */
    @Bean
    @Order(1)
    SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/login",
                        "/api/token",
                        "/api/noauth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain authenticatedSecurityFilterChain(
            HttpSecurity http,
            JsonSecurityHandlers jsonSecurityHandlers,
            IdentityJwtAuthenticationConverter identityJwtAuthenticationConverter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/me/**")
                        .authenticated()
                        .requestMatchers("/api/tenants/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/logout")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/logout-all")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(identityJwtAuthenticationConverter)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonSecurityHandlers)
                        .accessDeniedHandler(jsonSecurityHandlers));
        return http.build();
    }
}
