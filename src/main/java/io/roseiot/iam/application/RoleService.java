package io.roseiot.iam.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.domain.ResourceType;
import io.roseiot.iam.persistence.*;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.shared.util.UuidV7;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.tenant.persistence.TenantInvitation;
import io.roseiot.tenant.persistence.TenantInvitationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RoleService {
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String DEVICE_OPERATOR = "DEVICE_OPERATOR";
    public static final String DEVICE_VIEWER = "DEVICE_VIEWER";

    private final RoleMapper roles;
    private final RoleResourceMapper actions;
    private final ResourceService resources;
    private final TenantMapper tenants;
    private final UserRoleMapper assignments;
    private final UserMapper users;
    private final UserSessionMapper sessions;
    private final TenantInvitationMapper invitations;

    public RoleService(RoleMapper roles, RoleResourceMapper actions, ResourceService resources, TenantMapper tenants,
                       UserRoleMapper assignments, UserMapper users,
                       UserSessionMapper sessions, TenantInvitationMapper invitations) {
        this.roles = roles;
        this.actions = actions;
        this.resources = resources;
        this.tenants = tenants;
        this.assignments = assignments;
        this.users = users;
        this.sessions = sessions;
        this.invitations = invitations;
    }

    public Role requireAssignable(UUID tenantId, UUID roleId) {
        tenants.requireCustomerTenant(tenantId);
        Role role = roles.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()))
            throw new BusinessException(ApiErrorCode.ROLE_NOT_FOUND);
        return role;
    }

    public List<View> list(UUID tenantId) {
        tenants.requireCustomerTenant(tenantId);
        tenants.requireActive(tenantId);
        return roles.selectList(Wrappers.lambdaQuery(Role.class).eq(Role::getTenantId, tenantId).orderByAsc(Role::getCode))
                .stream().map(this::view).toList();
    }

    @Transactional
    public void initializeBuiltInRoles(UUID tenantId) {
        tenants.requireCustomerTenant(tenantId);
        List<BuiltInRole> builtIns = List.of(
                new BuiltInRole(TENANT_ADMIN, "Administrator", Set.of(
                        ActionCode.TENANT_USER_MANAGE, ActionCode.TENANT_USER_INVITE, ActionCode.TENANT_ROLE_MANAGE)),
                new BuiltInRole(DEVICE_OPERATOR, "Operator", Set.of(
                        ActionCode.DEVICE_READ, ActionCode.DEVICE_COMMAND_ISSUE, ActionCode.ALARM_ACK)),
                new BuiltInRole(DEVICE_VIEWER, "Viewer", Set.of(ActionCode.DEVICE_READ)));
        for (BuiltInRole definition : builtIns) {
            Role role = roles.selectByCode(tenantId, definition.code());
            if (role == null) {
                role = new Role();
                role.setId(UuidV7.generate());
                role.setTenantId(tenantId);
                role.setCode(definition.code());
                role.setName(definition.name());
                role.setBuiltIn(true);
                roles.insert(role);
            } else if (!role.isBuiltIn()) {
                throw new IllegalStateException("Built-in role code is occupied by a custom role");
            }
            replaceActions(role, definition.actions());
        }
    }

    @Transactional
    public View create(UUID tenantId, String code, String name, Set<String> actionCodes) {
        tenants.requireCustomerTenant(tenantId);
        tenants.requireActiveForUpdate(tenantId);
        if (code == null || !code.matches("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*") || code.length() > 64
                || Set.of(TENANT_ADMIN, DEVICE_OPERATOR, DEVICE_VIEWER).contains(code))
            throw new IllegalArgumentException("Invalid or reserved role code");
        if (roles.selectByCode(tenantId, code) != null) throw new BusinessException(ApiErrorCode.ROLE_CODE_CONFLICT);
        requireAssignableCodes(tenantId, actionCodes);
        Role r = new Role();
        r.setId(UuidV7.generate());
        r.setTenantId(tenantId);
        r.setCode(code);
        r.setName(name.trim());
        roles.insert(r);
        replaceActions(r, actionCodes);
        return view(r);
    }

    @Transactional
    public View update(UUID tenantId, UUID id, String name, Set<String> actionCodes) {
        tenants.requireActiveForUpdate(tenantId);
        Role r = requireAssignable(tenantId, id);
        requireCustom(r);
        requireAssignableCodes(tenantId, actionCodes);
        r.setName(name.trim());
        roles.updateById(r);
        replaceActions(r, actionCodes);
        ensureAdministrator(tenantId);
        invalidateMembers(r);
        return view(r);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        tenants.requireActiveForUpdate(tenantId);
        Role r = requireAssignable(tenantId, id);
        requireCustom(r);
        if (assignments.selectCount(Wrappers.lambdaQuery(UserRole.class).eq(UserRole::getRoleId, id)) > 0
                || invitations.selectCount(Wrappers.lambdaQuery(TenantInvitation.class)
                .eq(TenantInvitation::getRoleId, id)) > 0) throw new BusinessException(ApiErrorCode.ROLE_IN_USE);
        actions.delete(Wrappers.lambdaQuery(RoleResource.class).eq(RoleResource::getRoleId, id));
        roles.deleteById(id);
    }

    public void ensureAdministrator(UUID tenantId) {
        if (assignments.countActiveTenantUsersWithRole(tenantId, TENANT_ADMIN) < 1)
            throw new BusinessException(ApiErrorCode.LAST_TENANT_ADMIN);
    }

    private void requireCustom(Role r) {
        if (r.isBuiltIn()) throw new BusinessException(ApiErrorCode.ROLE_PROTECTED);
    }

    private void requireAssignableCodes(UUID tenantId, Set<String> actionCodes) {
        if (actionCodes == null) throw new IllegalArgumentException("actionCodes is required");
        Set<String> allowed = new HashSet<>();
        resources.assignableActions(tenantId).forEach(a -> allowed.add(a.getCode()));
        if (!allowed.containsAll(actionCodes)) throw new BusinessException(ApiErrorCode.INVALID_ACTION);
    }

    private void replaceActions(Role role, Set<String> codes) {
        if (codes == null) throw new IllegalArgumentException("actionCodes is required");
        Map<String, Resource> byCode = new HashMap<>();
        resources.findActiveActions(codes).forEach(a -> byCode.put(a.getCode(), a));
        if (!byCode.keySet().containsAll(codes)) throw new BusinessException(ApiErrorCode.INVALID_ACTION);
        actions.delete(Wrappers.lambdaQuery(RoleResource.class).eq(RoleResource::getRoleId, role.getId()));
        for (String code : codes) {
            RoleResource link = new RoleResource();
            link.setTenantId(role.getTenantId());
            link.setRoleId(role.getId());
            link.setResourceId(byCode.get(code).getId());
            actions.insert(link);
        }
    }

    private void invalidateMembers(Role role) {
        var members = assignments.selectList(Wrappers.lambdaQuery(UserRole.class).eq(UserRole::getRoleId, role.getId()));
        for (var member : members) {
            users.update(null, Wrappers.lambdaUpdate(User.class).eq(User::getId, member.getUserId())
                    .eq(User::getTenantId, role.getTenantId()).setSql("authorization_version = authorization_version + 1"));
            sessions.update(null, Wrappers.lambdaUpdate(UserSession.class).eq(UserSession::getUserId, member.getUserId())
                    .isNull(UserSession::getRevokedAt).set(UserSession::getRevokedAt, Instant.now()));
        }
    }

    private View view(Role r) {
        Set<UUID> ids = new HashSet<>();
        actions.selectList(Wrappers.lambdaQuery(RoleResource.class).eq(RoleResource::getRoleId, r.getId()))
                .forEach(a -> ids.add(a.getResourceId()));
        return new View(r.getId(), r.getCode(), r.getName(), r.isBuiltIn(), resources.catalog().stream()
                .filter(a -> ids.contains(a.getId()) && a.getType() == ResourceType.ACTION)
                .map(Resource::getCode).sorted().toList());
    }

    public record View(UUID id, String code, String name, boolean builtIn, List<String> actionCodes) {}

    private record BuiltInRole(String code, String name, Set<String> actions) {}
}
