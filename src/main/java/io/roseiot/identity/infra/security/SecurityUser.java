package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.UUID;

/** Loaded by {@link SecurityUserDetailsService}; access JWT adds {@code sid} only. */
public final class SecurityUser implements UserDetails {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;
    private final UUID sessionId;

    public SecurityUser(User user, Collection<? extends GrantedAuthority> authorities) {
        this(user, authorities, null);
    }

    private SecurityUser(User user, Collection<? extends GrantedAuthority> authorities, UUID sessionId) {
        this.user = user;
        this.authorities = authorities;
        this.sessionId = sessionId;
    }

    SecurityUser withSessionId(Jwt jwt) {
        return new SecurityUser(user, authorities, readOptionalUuid(jwt, "sid"));
    }

    public UUID userId() {
        return user.getId();
    }

    public long authorizationVersion() {
        return user.getAuthorizationVersion();
    }

    public UUID tenantId() {
        return user.getTenantId();
    }

    /** {@code users.tenant_id IS NULL} — 系统用户，否则为租户用户。 */
    public boolean isSystemUser() {
        return tenantId() == null;
    }

    public boolean isTenantUser() {
        return tenantId() != null;
    }

    public UUID requireTenantId() {
        if (tenantId() == null) {
            throw new AccessDeniedException("Forbidden");
        }
        return tenantId();
    }

    public UUID requireSessionId() {
        if (sessionId == null) {
            throw new AccessDeniedException("Forbidden");
        }
        return sessionId;
    }

    static Long authorizationVersion(Jwt jwt) {
        Object claim = jwt.getClaim(IdentityJwtClaims.AUTHORIZATION_VERSION);
        return claim instanceof Number number ? number.longValue() : null;
    }

    static SecurityUser fromJwtClaimsOnly(Jwt jwt) {
        Long azv = authorizationVersion(jwt);
        if (azv == null) {
            throw new AccessDeniedException("Forbidden");
        }
        User stub = new User();
        stub.setId(UUID.fromString(jwt.getSubject()));
        stub.setAuthorizationVersion(Math.toIntExact(azv));
        String tid = jwt.getClaimAsString("tid");
        if (tid != null && !tid.isBlank()) {
            stub.setTenantId(UUID.fromString(tid));
        }
        return new SecurityUser(stub, java.util.List.of(), readOptionalUuid(jwt, "sid"));
    }

    private static UUID readOptionalUuid(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getDisabledAt() == null;
    }

    @Override
    public String toString() {
        return userId().toString();
    }
}
