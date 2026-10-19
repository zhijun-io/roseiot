package io.roseiot.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration(proxyBeanMethods = false)
class AuthSecurityConfiguration {

    @Bean
    SecretKey authSigningKey(AuthJwtProperties properties) {
        byte[] key = Base64.getDecoder().decode(properties.hmacSecret());
        if (key.length < 32) {
            throw new IllegalArgumentException("ROSEIOT_JWT_HMAC_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey authSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(authSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey authSigningKey, SessionLifecycle sessions) {
        var decoder = NimbusJwtDecoder.withSecretKey(authSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var defaults = JwtValidators.createDefaultWithIssuer("roseiot");
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", audience -> audience != null && audience.contains("roseiot-api"));
        OAuth2TokenValidator<Jwt> sessionValidator = jwt -> {
            String sidClaim = jwt.getClaimAsString("sid");
            long userId;
            long sid;
            try {
                userId = Long.parseLong(jwt.getSubject());
                sid = Long.parseLong(sidClaim);
            } catch (RuntimeException invalidClaims) {
                return invalidSession();
            }
            try {
                return sessions.isActive(sid, userId, Instant.now())
                        ? OAuth2TokenValidatorResult.success()
                        : invalidSession();
            } catch (DataAccessException unavailable) {
                throw new SessionStateUnavailableException(unavailable);
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, audienceValidator, sessionValidator));
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidSession() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The access token is not valid.", null));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // The controlled bootstrap SQL creates raw BCrypt hashes, so this encoder matches its stored format.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsServiceAdapter users, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    @Bean
    SecurityFilterChain apiSecurityChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        AuthenticationEntryPoint entryPoint = (request, response, failure) -> {
            if (failure instanceof SessionStateUnavailableException) {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter()
                        .write("{\"type\":\"about:blank\",\"title\":\"Service Unavailable\","
                                + "\"status\":503,\"detail\":\"Authentication is temporarily unavailable.\"}");
            } else {
                bearerEntryPoint.commence(request, response, failure);
            }
        };
        AccessDeniedHandler deniedHandler = new BearerTokenAccessDeniedHandler();
        return http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**")
                        .permitAll()
                        .requestMatchers("/api/me/**")
                        .access((authentication, context) -> {
                            var auth = authentication.get();
                            if (!(auth.getPrincipal() instanceof Jwt jwt)) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }
                            boolean firstLoginPasswordChange =
                                    Boolean.TRUE.equals(jwt.getClaim("password_change_required"));
                            String path = context.getRequest().getRequestURI();
                            boolean allowed =
                                    HttpMethod.PUT.matches(context.getRequest().getMethod())
                                                    && path.equals("/api/me/password")
                                            || HttpMethod.DELETE.matches(
                                                            context.getRequest().getMethod())
                                                    && path.equals("/api/me/sessions/current");
                            return new org.springframework.security.authorization.AuthorizationDecision(
                                    !firstLoginPasswordChange || allowed);
                        })
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}

final class SessionStateUnavailableException extends AuthenticationServiceException {
    SessionStateUnavailableException(Throwable cause) {
        super("Session state is temporarily unavailable", cause);
    }
}
