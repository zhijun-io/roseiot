package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** SpEL target {@code @authz} for {@link org.springframework.security.access.prepost.PreAuthorize}. */
@Service("authz")
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class RoseiotAuthorization {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public RoseiotAuthorization(UserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public boolean hasPermission(Authentication authentication, String permission) {
        return permitted(user(authentication), permission);
    }

    public boolean hasSystemPermission(Authentication authentication, String permission) {
        SecurityUser user = user(authentication);
        return user.isSystemContext() && permitted(user, permission);
    }

    public boolean hasTenantPermission(Authentication authentication, String permission) {
        SecurityUser user = user(authentication);
        return user.isTenantContext() && user.tenantId() != null && permitted(user, permission);
    }

    public boolean hasTenantInviteOrManage(Authentication authentication) {
        SecurityUser user = user(authentication);
        if (!user.isTenantContext() || user.tenantId() == null) {
            return false;
        }
        return permitted(user, IdentityJwtClaims.PERMISSION_TENANT_USER_INVITE)
                || permitted(user, IdentityJwtClaims.PERMISSION_TENANT_USER_MANAGE);
    }

    public boolean canPatchTenant(Authentication authentication, Boolean disabled, String name) {
        SecurityUser user = user(authentication);
        if (!user.isSystemContext()) {
            return false;
        }
        if (disabled != null) {
            String permission = disabled
                    ? IdentityJwtClaims.PERMISSION_TENANT_DISABLE
                    : IdentityJwtClaims.PERMISSION_TENANT_ENABLE;
            if (!permitted(user, permission)) {
                return false;
            }
        }
        if (name != null && !name.isBlank() && !permitted(user, IdentityJwtClaims.PERMISSION_TENANT_CREATE)) {
            return false;
        }
        return true;
    }

    private static SecurityUser user(Authentication authentication) {
        return SecurityUser.from(authentication);
    }

    private boolean permitted(SecurityUser user, String permission) {
        User stored = userMapper.selectById(user.userId());
        if (stored == null || stored.getDisabledAt() != null) {
            return false;
        }
        if (stored.getAuthorizationVersion() != user.authorizationVersion()) {
            return false;
        }
        return userRoleMapper.userHasPermission(user.userId(), permission);
    }
}
