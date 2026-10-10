package io.roseiot.iam.infra.security;

import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserCredential;
import io.roseiot.iam.domain.UserStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

/** Current user and authorities loaded for an authenticated request. */
public final class SecurityUser implements UserDetails {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;
    private final UserCredential credential;

    public SecurityUser(User user, Collection<? extends GrantedAuthority> authorities) {
        this(user, null, authorities);
    }

    public SecurityUser(User user, UserCredential credential, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.credential = credential;
        this.authorities = authorities;
    }

    public UUID userId() {
        return user.getId();
    }

    public UUID tenantId() {
        return user.getTenantId();
    }

    public UUID requireTenantId() {
        if (tenantId() == null) {
            throw new AccessDeniedException("Forbidden");
        }
        return tenantId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return credential == null ? null : credential.getPassword();
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
        return user.getStatus() == UserStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return userId().toString();
    }
}
