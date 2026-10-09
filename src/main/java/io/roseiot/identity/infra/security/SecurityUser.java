package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Authenticated human principal; built once in {@link IdentityJwtAuthenticationConverter}. */
public record SecurityUser(
        UUID userId,
        long authorizationVersion,
        String context,
        UUID tenantId,
        UUID sessionId) {

    public boolean isSystemContext() {
        return IdentityJwtClaims.CONTEXT_SYSTEM.equals(context);
    }

    public boolean isTenantContext() {
        return IdentityJwtClaims.CONTEXT_TENANT.equals(context);
    }

    public UUID requireTenantId() {
        if (!isTenantContext() || tenantId == null) {
            throw new AccessDeniedException("Forbidden");
        }
        return tenantId;
    }

    public UUID requireSessionId() {
        if (sessionId == null) {
            throw new AccessDeniedException("Forbidden");
        }
        return sessionId;
    }

    @Override
    public String toString() {
        return userId.toString();
    }

    static SecurityUser from(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("Forbidden");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser user) {
            return user;
        }
        if (principal instanceof Jwt jwt) {
            return fromJwt(jwt);
        }
        throw new AccessDeniedException("Forbidden");
    }

    static SecurityUser fromJwt(Jwt jwt, User validatedUser) {
        return build(jwt, validatedUser.getId(), validatedUser.getAuthorizationVersion());
    }

    static Long authorizationVersion(Jwt jwt) {
        Object claim = jwt.getClaim(IdentityJwtClaims.AUTHORIZATION_VERSION);
        return claim instanceof Number number ? number.longValue() : null;
    }

    private static SecurityUser fromJwt(Jwt jwt) {
        Long azv = authorizationVersion(jwt);
        if (azv == null) {
            throw new AccessDeniedException("Forbidden");
        }
        return build(jwt, UUID.fromString(jwt.getSubject()), azv);
    }

    private static SecurityUser build(Jwt jwt, UUID userId, long authorizationVersion) {
        return new SecurityUser(
                userId,
                authorizationVersion,
                jwt.getClaimAsString(IdentityJwtClaims.CONTEXT),
                readOptionalUuid(jwt, IdentityJwtClaims.TENANT_ID),
                readOptionalUuid(jwt, "sid"));
    }

    private static UUID readOptionalUuid(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}
