package io.roseiot.iam.role;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.resource.ResourceType;
import io.roseiot.iam.resource.Resource;
import io.roseiot.iam.resource.ResourceService;
import io.roseiot.tenant.TenantService;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.webmvc.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RoleService {
    private final RoleMapper roleMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final ResourceService resourceService;
    private final TenantService tenantService;

    public RoleService(RoleMapper roleMapper, RoleResourceMapper roleResourceMapper, ResourceService resourceService, TenantService tenantService) {
        this.roleMapper = roleMapper;
        this.roleResourceMapper = roleResourceMapper;
        this.resourceService = resourceService;
        this.tenantService = tenantService;
    }

    public Role requireAssignable(Long tenantId, Long roleId) {
        RoleEntity role = requireEntity(tenantId, roleId);
        return Role.from(role);
    }

    public CursorPage<RoleInfo> list(Long tenantId, String cursor, int limit) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        long afterId = CursorPage.afterId(cursor);
        int pageSize = CursorPage.checkedLimit(limit);
        List<RoleInfo> rows = roleMapper.selectList(Wrappers.lambdaQuery(RoleEntity.class)
                        .eq(RoleEntity::getTenantId, tenantId).gt(RoleEntity::getId, afterId)
                        .orderByAsc(RoleEntity::getId).last("LIMIT " + (pageSize + 1)))
                .stream().map(this::view).toList();
        return CursorPage.fromRows(rows, pageSize, RoleInfo::id);
    }

    public RoleInfo get(Long tenantId, Long roleId) {
        return view(requireEntity(tenantId, roleId));
    }

    @Transactional
    public RoleInfo create(Long tenantId, String name, Set<String> actionCodes) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActiveForUpdate(tenantId);
        if (name == null || name.isBlank() || name.length() > 255)
            throw new IllegalArgumentException("Invalid role name");
        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setTenantId(tenantId);
        roleEntity.setName(name.trim());
        roleMapper.insert(roleEntity);
        replaceActions(roleEntity, actionCodes);
        return view(roleEntity);
    }

    @Transactional
    public RoleInfo update(Long tenantId, Long id, String name, Set<String> actionCodes) {
        tenantService.requireActiveForUpdate(tenantId);
        RoleEntity roleEntity = requireEntity(tenantId, id);
        roleEntity.setName(name.trim());
        roleMapper.updateById(roleEntity);
        replaceActions(roleEntity, actionCodes);
        ensureAdministrator(tenantId);
        return view(roleEntity);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        tenantService.requireActiveForUpdate(tenantId);
        RoleEntity roleEntity = requireEntity(tenantId, id);
        if (roleMapper.hasAssignments(id) > 0)
            throw new BusinessException(ApiErrorCode.ROLE_IN_USE);
        roleResourceMapper.delete(Wrappers.lambdaQuery(RoleResourceEntity.class).eq(RoleResourceEntity::getRoleId, id));
        roleMapper.deleteById(id);
    }

    public void ensureAdministrator(Long tenantId) {
        if (roleMapper.countActiveTenantUsersWithRole(tenantId) < 1)
            throw new BusinessException(ApiErrorCode.LAST_TENANT_ADMIN);
    }

    private RoleEntity requireEntity(Long tenantId, Long roleId) {
        tenantService.requireCustomerTenant(tenantId);
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()))
            throw new BusinessException(ApiErrorCode.ROLE_NOT_FOUND);
        return role;
    }

    private void replaceActions(RoleEntity role, Set<String> codes) {
        Map<String, Resource> byCode = new HashMap<>();
        resourceService.findAssignableActions(codes)
                .forEach(actionResource -> byCode.put(actionResource.code(), actionResource));
        if (!byCode.keySet().containsAll(codes)) throw new BusinessException(ApiErrorCode.INVALID_ACTION);
        roleResourceMapper.delete(Wrappers.lambdaQuery(RoleResourceEntity.class).eq(RoleResourceEntity::getRoleId, role.getId()));
        for (String code : codes) {
            RoleResourceEntity link = new RoleResourceEntity();
            link.setTenantId(role.getTenantId());
            link.setRoleId(role.getId());
            link.setResourceId(byCode.get(code).id());
            roleResourceMapper.insert(link);
        }
    }

    private RoleInfo view(RoleEntity roleEntity) {
        Set<Long> assignedResourceIds = new HashSet<>();
        roleResourceMapper.selectList(Wrappers.lambdaQuery(RoleResourceEntity.class).eq(RoleResourceEntity::getRoleId, roleEntity.getId()))
                .forEach(roleResource -> assignedResourceIds.add(roleResource.getResourceId()));
        return new RoleInfo(roleEntity.getId(), roleEntity.getName(), resourceService.catalog().stream()
                .filter(resource -> assignedResourceIds.contains(resource.id()) && resource.type() == ResourceType.ACTION)
                .map(Resource::code).sorted().toList());
    }
}
