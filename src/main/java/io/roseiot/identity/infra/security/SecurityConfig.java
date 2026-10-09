package io.roseiot.identity.infra.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonSecurityHandlers jsonSecurityHandlers,
            IdentityJwtAuthenticationConverter identityJwtAuthenticationConverter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/token")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/recovery/request")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/recovery/confirm")
                        .permitAll()
                        .requestMatchers("/api/users/me/**")
                        .authenticated()
                        .requestMatchers("/api/tenants", "/api/tenants/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout-all")
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
