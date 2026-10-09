package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class IdentityJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

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

        Long tokenAzv = SecurityUser.authorizationVersion(jwt);
        if (tokenAzv == null) {
            throw invalidToken("Missing authorization version");
        }
        if (user.getAuthorizationVersion() != tokenAzv) {
            throw invalidToken("Authorization version mismatch");
        }

        String context = jwt.getClaimAsString(IdentityJwtClaims.CONTEXT);
        if (IdentityJwtClaims.CONTEXT_TENANT.equals(context)) {
            String tenantId = jwt.getClaimAsString(IdentityJwtClaims.TENANT_ID);
            if (user.getTenantId() == null || tenantId == null
                    || !user.getTenantId().toString().equals(tenantId)) {
                throw invalidToken("Tenant context mismatch");
            }
        } else if (IdentityJwtClaims.CONTEXT_SYSTEM.equals(context) && user.getTenantId() != null) {
            throw invalidToken("System context mismatch");
        }

        SecurityUser principal = SecurityUser.fromJwt(jwt, user);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    private static OAuth2AuthenticationException invalidToken(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null));
    }
}
