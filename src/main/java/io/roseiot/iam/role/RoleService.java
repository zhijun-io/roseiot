package io.roseiot.iam.role;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.resource.Resource;
import io.roseiot.iam.resource.ResourceService;
import io.roseiot.tenant.TenantService;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
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

    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public org.springframework.data.domain.Page<Role> list(Long tenantId, Pageable pageable) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        Pageable checkedPageable = checkedPageable(pageable);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RoleEntity> result = roleMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        checkedPageable.getPageNumber() + 1L, checkedPageable.getPageSize()),
                Wrappers.lambdaQuery(RoleEntity.class)
                        .eq(RoleEntity::getTenantId, tenantId)
                        .orderByAsc(RoleEntity::getId));
        return new PageImpl<>(result.getRecords().stream().map(Role::from).toList(),
                checkedPageable, result.getTotal());
    }

    private static Pageable checkedPageable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100
                || !Sort.by(Sort.Order.asc("id")).equals(pageable.getSort())) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Use a page size from 1 to 100 and sort by id ascending");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
    }

    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public Role get(Long tenantId, Long roleId) {
        return Role.from(requireEntity(tenantId, roleId));
    }

    @Transactional
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public Role create(Long tenantId, String name, Set<String> actionCodes) {
        validateActionCodes(actionCodes);
        String validatedName = validateName(name);
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActiveForUpdate(tenantId);
        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setTenantId(tenantId);
        roleEntity.setName(validatedName);
        roleMapper.insert(roleEntity);
        replaceActions(roleEntity, actionCodes);
        return Role.from(roleEntity);
    }

    @Transactional
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public Role update(Long tenantId, Long id, String name, Set<String> actionCodes) {
        validateActionCodes(actionCodes);
        String validatedName = validateName(name);
        tenantService.requireActiveForUpdate(tenantId);
        RoleEntity roleEntity = requireEntity(tenantId, id);
        roleEntity.setName(validatedName);
        roleMapper.updateById(roleEntity);
        replaceActions(roleEntity, actionCodes);
        ensureAdministrator(tenantId);
        return Role.from(roleEntity);
    }

    @Transactional
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public void delete(Long tenantId, Long id) {
        tenantService.requireActiveForUpdate(tenantId);
        RoleEntity roleEntity = requireEntity(tenantId, id);
        if (roleMapper.hasAssignments(tenantId, id) > 0)
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
            RoleResource assignment = new RoleResource(role.getTenantId(), role.getId(), byCode.get(code).id());
            roleResourceMapper.insert(RoleResourceEntity.from(assignment));
        }
    }

    private static void validateActionCodes(Set<String> actionCodes) {
        if (actionCodes == null || actionCodes.size() > 100
                || actionCodes.stream().anyMatch(code -> code == null || code.isBlank() || code.length() > 128)) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Invalid action codes");
        }
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 255) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Role name must contain 1 to 255 characters");
        }
        return name.trim();
    }

}
