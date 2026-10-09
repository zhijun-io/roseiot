package io.roseiot.tenant.application;

import io.roseiot.identity.persistence.Role;
import io.roseiot.identity.persistence.RoleMapper;
import io.roseiot.identity.persistence.TenantUserRow;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRole;
import io.roseiot.identity.persistence.UserRoleMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.persistence.TenantMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class TenantUserService {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public TenantUserService(
            TenantMapper tenantMapper,
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public List<TenantUserSummary> listUsers(UUID tenantId) {
        tenantMapper.requireActive(tenantId);
        return userRoleMapper.listTenantUsers(tenantId).stream()
                .map(TenantUserService::toSummary)
                .toList();
    }

    @Transactional
    public void updateUser(UUID tenantId, UUID userId, Boolean disabled, String targetRole) {
        tenantMapper.requireActive(tenantId);
        if (disabled == null && (targetRole == null || targetRole.isBlank())) {
            throw new IllegalArgumentException("No changes requested");
        }

        User user = userMapper.selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        }

        String currentRole = requireRoleCode(userId);
        boolean currentlyDisabled = user.getDisabledAt() != null;

        if (disabled != null && disabled) {
            if (!currentlyDisabled && TenantRoles.TENANT_ADMIN.equals(currentRole)) {
                ensureNotLastActiveTenantAdmin(tenantId, userId);
            }
            if (!currentlyDisabled) {
                user.setDisabledAt(Instant.now());
                userMapper.updateById(user);
            }
        } else if (disabled != null) {
            if (currentlyDisabled) {
                user.setDisabledAt(null);
                userMapper.updateById(user);
            }
        }

        if (targetRole != null && !targetRole.isBlank()) {
            TenantRoles.requireAssigneeRoleCode(targetRole);
            if (TenantRoles.TENANT_ADMIN.equals(currentRole)
                    && !TenantRoles.TENANT_ADMIN.equals(targetRole)
                    && user.getDisabledAt() == null) {
                ensureNotLastActiveTenantAdmin(tenantId, userId);
            }
            if (!targetRole.equals(currentRole)) {
                userRoleMapper.replaceRoleInTenantScope(
                        userId, tenantId, roleMapper.requireIdByCode(targetRole));
            }
        }
    }

    private String requireRoleCode(UUID userId) {
        UserRole assignment = userRoleMapper.selectFirstByUserId(userId);
        if (assignment == null) {
            throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        }
        Role role = roleMapper.selectById(assignment.getRoleId());
        if (role == null) {
            throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        }
        return role.getCode();
    }

    private void ensureNotLastActiveTenantAdmin(UUID tenantId, UUID userId) {
        int activeAdmins = userRoleMapper.countActiveTenantUsersWithRole(tenantId, TenantRoles.TENANT_ADMIN);
        if (activeAdmins <= 1) {
            throw new BusinessException(ApiErrorCode.LAST_TENANT_ADMIN);
        }
    }

    private static TenantUserSummary toSummary(TenantUserRow row) {
        return new TenantUserSummary(row.getId(), row.getEmail(), row.getRoleCode(), row.getDisabledAt() != null);
    }

    public record TenantUserSummary(UUID id, String email, String role, boolean disabled) {}
}
