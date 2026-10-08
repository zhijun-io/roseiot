package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdentityJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
    private final UserMapper userMapper;

    public IdentityJwtAuthenticationConverter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw invalidToken("Invalid subject");
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getDisabledAt() != null) {
            throw invalidToken("User not active");
        }

        long tokenAzv = readAuthorizationVersion(jwt);
        if (user.getAuthorizationVersion() != tokenAzv) {
            throw invalidToken("Authorization version mismatch");
        }

        return delegate.convert(jwt);
    }

    private static long readAuthorizationVersion(Jwt jwt) {
        Object claim = jwt.getClaim(IdentityJwtClaims.AUTHORIZATION_VERSION);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        throw invalidToken("Missing authorization version");
    }

    private static OAuth2AuthenticationException invalidToken(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null));
    }
}
