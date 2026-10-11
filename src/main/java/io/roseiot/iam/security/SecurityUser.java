package io.roseiot.iam.security;

import io.roseiot.iam.user.User;
import io.roseiot.iam.user.UserStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/** Current user and authorities loaded for an authenticated request. */
public final class SecurityUser implements UserDetails, io.roseiot.shared.security.TenantPrincipal {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String passwordHash;

    public SecurityUser(User user, String passwordHash, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.authorities = authorities;
    }

    public Long userId() {
        return user.id();
    }

    public Long tenantId() {
        return user.tenantId();
    }

    public Long requireTenantId() {
        if (tenantId() == null) {
            throw new AccessDeniedException("Forbidden");
        }
        return tenantId();
    }

    public void requireTenant(Long tenantId) {
        if (!requireTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return user.email();
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
        return user.status() == UserStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return userId().toString();
    }
}
