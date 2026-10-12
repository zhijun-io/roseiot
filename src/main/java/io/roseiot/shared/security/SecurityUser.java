package io.roseiot.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Current user and authorities loaded for an authenticated request. */
public final class SecurityUser implements UserDetails, TenantPrincipal {

    private final Long userId;
    private final Long tenantId;
    private final String username;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String password;

    public SecurityUser(
            Long userId,
            Long tenantId,
            String username,
            boolean enabled,
            String password,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.enabled = enabled;
        this.password = password;
        this.authorities = List.copyOf(authorities);
    }

    public Long userId() {
        return userId;
    }

    public Long tenantId() {
        return tenantId;
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
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
        return enabled;
    }

    @Override
    public String toString() {
        return userId().toString();
    }
}
