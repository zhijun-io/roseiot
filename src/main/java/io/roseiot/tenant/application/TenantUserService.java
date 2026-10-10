package io.roseiot.tenant.application;

import io.roseiot.identity.application.RoleService;
import io.roseiot.identity.application.UserSessionService;
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
    private final UserSessionService userSessionService;

    public TenantUserService(
            TenantMapper tenantMapper,
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            UserSessionService userSessionService) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userSessionService = userSessionService;
    }

    public List<TenantUserSummary> listUsers(UUID tenantId) {
        tenantMapper.requireActive(tenantId);
        return userRoleMapper.listTenantUsers(tenantId).stream()
                .map(TenantUserService::toSummary)
                .toList();
    }

    @Transactional
    public void updateUser(UUID tenantId, UUID userId, Boolean disabled, String targetRole) {
        // Serialize changes before checking the last-admin invariant.
        tenantMapper.requireActiveForUpdate(tenantId);
        String newRole = targetRole == null || targetRole.isBlank() ? null : targetRole.trim();
        if (disabled == null && newRole == null) {
            throw new IllegalArgumentException("No changes requested");
        }

        User user = userMapper.selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        }

        String currentRole = requireRoleCode(userId, tenantId);
        if (newRole != null) {
            TenantRoles.requireAssigneeRole(newRole);
        }
        boolean currentlyDisabled = user.getDisabledAt() != null;
        boolean willDisable = Boolean.TRUE.equals(disabled) && !currentlyDisabled;
        boolean willStayActive = !currentlyDisabled && !willDisable;
        boolean demotesActiveAdmin = newRole != null
                && RoleService.TENANT_ADMIN.equals(currentRole)
                && !RoleService.TENANT_ADMIN.equals(newRole)
                && willStayActive;
        if ((willDisable || demotesActiveAdmin) && RoleService.TENANT_ADMIN.equals(currentRole)) {
            ensureNotLastActiveTenantAdmin(tenantId, userId);
        }

        boolean changed = false;
        if (disabled != null && disabled != currentlyDisabled) {
            user.setDisabledAt(disabled ? Instant.now() : null);
            changed = true;
        }
        if (newRole != null && !newRole.equals(currentRole)) {
            userRoleMapper.replaceRoleInTenantScope(
                    userId, tenantId, roleMapper.requireIdByCode(newRole));
            changed = true;
        }
        if (changed) {
            user.setAuthorizationVersion(user.getAuthorizationVersion() + 1);
            userMapper.updateById(user);
            userSessionService.logoutAllSessions(userId);
        }
    }

    private String requireRoleCode(UUID userId, UUID tenantId) {
        UserRole assignment = userRoleMapper.selectTenantAssignment(userId, tenantId);
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
        int activeAdmins = userRoleMapper.countActiveTenantUsersWithRole(tenantId, RoleService.TENANT_ADMIN);
        if (activeAdmins <= 1) {
            throw new BusinessException(ApiErrorCode.LAST_TENANT_ADMIN);
        }
    }

    private static TenantUserSummary toSummary(TenantUserRow row) {
        return new TenantUserSummary(row.getId(), row.getEmail(), row.getRoleCode(), row.getDisabledAt() != null);
    }

    public record TenantUserSummary(UUID id, String email, String role, boolean disabled) {}
}
