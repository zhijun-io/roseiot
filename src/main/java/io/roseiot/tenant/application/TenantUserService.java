package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.application.RoleService;
import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.iam.persistence.UserRole;
import io.roseiot.iam.persistence.UserRoleMapper;
import io.roseiot.iam.domain.UserStatus;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.shared.util.UuidV7;
import io.roseiot.tenant.persistence.TenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TenantUserService {
    private final TenantMapper tenants;
    private final UserMapper users;
    private final UserRoleMapper assignments;
    private final RoleService roles;
    private final UserSessionService sessions;

    public TenantUserService(TenantMapper tenants, UserMapper users, UserRoleMapper assignments,
                             RoleService roles, UserSessionService sessions) {
        this.tenants = tenants;
        this.users = users;
        this.assignments = assignments;
        this.roles = roles;
        this.sessions = sessions;
    }
    public List<TenantUserSummary> listUsers(UUID tenantId) {
        tenants.requireCustomerTenant(tenantId);
        tenants.requireActive(tenantId);
        return assignments.listTenantUsers(tenantId).stream()
                .map(r -> new TenantUserSummary(r.getId(), r.getEmail(), r.getRoleId(), r.getStatus() != UserStatus.ACTIVE)).toList();
    }
    @Transactional
    public void updateUser(UUID tenantId, UUID userId, Boolean disabled, UUID roleId) {
        tenants.requireCustomerTenant(tenantId);
        tenants.requireActiveForUpdate(tenantId);
        if (disabled == null && roleId == null) throw new IllegalArgumentException("No changes requested");
        User user = users.selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId()))
            throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        if (roleId != null) {
            roles.requireAssignable(tenantId, roleId);
            assignments.delete(Wrappers.lambdaQuery(UserRole.class).eq(UserRole::getUserId, userId).eq(UserRole::getTenantId, tenantId));
            assignments.insertAssignment(UuidV7.generate(), userId, roleId, tenantId);
        }
        if (disabled != null) user.setStatus(disabled ? UserStatus.DISABLED : UserStatus.ACTIVE);
        user.setAuthorizationVersion(user.getAuthorizationVersion() + 1);
        users.update(null, Wrappers.lambdaUpdate(User.class).eq(User::getId, userId).eq(User::getTenantId, tenantId)
                .set(disabled != null, User::getStatus, user.getStatus())
                .setSql("authorization_version = authorization_version + 1"));
        roles.ensureAdministrator(tenantId);
        sessions.logoutAllSessions(userId);
    }

    public record TenantUserSummary(UUID id, String email, UUID roleId, boolean disabled) {
    }
}
