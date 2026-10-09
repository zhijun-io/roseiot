package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdentityJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SecurityUserDetailsService userDetailsService;

    public IdentityJwtAuthenticationConverter(SecurityUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw invalidToken("Invalid subject");
        }

        SecurityUser loaded;
        try {
            loaded = userDetailsService.loadByUserId(userId);
        } catch (UsernameNotFoundException exception) {
            throw invalidToken("User not active");
        }

        Long tokenAzv = SecurityUser.authorizationVersion(jwt);
        if (tokenAzv == null) {
            throw invalidToken("Missing authorization version");
        }
        if (loaded.authorizationVersion() != tokenAzv) {
            throw invalidToken("Authorization version mismatch");
        }

        SecurityUser principal = loaded.withSessionId(jwt);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, loaded.getAuthorities());
    }

    private static OAuth2AuthenticationException invalidToken(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null));
    }
}
