package io.roseiot.identity.infra.security;

import io.roseiot.identity.persistence.UserRoleMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Maps RBAC rows to Spring Security {@link org.springframework.security.core.GrantedAuthority} strings. */
final class RoseiotSecurityAuthorities {

    static final String ROLE_PREFIX = "ROLE_";

    private RoseiotSecurityAuthorities() {}

    static List<SimpleGrantedAuthority> forUser(UserRoleMapper userRoleMapper, UUID userId) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (String permission : userRoleMapper.listPermissionCodesForUser(userId)) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        for (String roleCode : userRoleMapper.listRoleCodesForUser(userId)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleCode));
        }
        return authorities;
    }
}
