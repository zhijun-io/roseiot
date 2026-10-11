package io.roseiot.iam.role;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.resource.ResourceType;
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

    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public org.springframework.data.domain.Page<RoleInfo> list(Long tenantId, Pageable pageable) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        Pageable checkedPageable = checkedPageable(pageable);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RoleEntity> result = roleMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        checkedPageable.getPageNumber() + 1L, checkedPageable.getPageSize()),
                Wrappers.lambdaQuery(RoleEntity.class)
                        .eq(RoleEntity::getTenantId, tenantId)
                        .orderByAsc(RoleEntity::getId));
        return new PageImpl<>(views(result.getRecords()), checkedPageable, result.getTotal());
    }

    private static Pageable checkedPageable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100
                || !Sort.by(Sort.Order.asc("id")).equals(pageable.getSort())) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Use a page size from 1 to 100 and sort by id ascending");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
    }

    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public RoleInfo get(Long tenantId, Long roleId) {
        return view(requireEntity(tenantId, roleId));
    }

    @Transactional
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public RoleInfo create(Long tenantId, String name, Set<String> actionCodes) {
        validateActionCodes(actionCodes);
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
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    public RoleInfo update(Long tenantId, Long id, String name, Set<String> actionCodes) {
        validateActionCodes(actionCodes);
        tenantService.requireActiveForUpdate(tenantId);
        RoleEntity roleEntity = requireEntity(tenantId, id);
        roleEntity.setName(name.trim());
        roleMapper.updateById(roleEntity);
        replaceActions(roleEntity, actionCodes);
        ensureAdministrator(tenantId);
        return view(roleEntity);
    }

    @Transactional
    @PreAuthorize("hasAuthority('tenant.role.manage')")
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

    private static void validateActionCodes(Set<String> actionCodes) {
        if (actionCodes == null || actionCodes.size() > 100
                || actionCodes.stream().anyMatch(code -> code == null || code.isBlank() || code.length() > 128)) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Invalid action codes");
        }
    }

    private RoleInfo view(RoleEntity roleEntity) {
        return views(List.of(roleEntity)).getFirst();
    }

    private List<RoleInfo> views(List<RoleEntity> roles) {
        if (roles.isEmpty()) return List.of();

        Map<Long, Set<Long>> resourceIdsByRole = new HashMap<>();
        roleResourceMapper.selectList(Wrappers.lambdaQuery(RoleResourceEntity.class)
                        .in(RoleResourceEntity::getRoleId, roles.stream().map(RoleEntity::getId).toList()))
                .forEach(assignment -> resourceIdsByRole
                        .computeIfAbsent(assignment.getRoleId(), ignored -> new HashSet<>())
                        .add(assignment.getResourceId()));

        List<Resource> catalogue = resourceService.catalog();
        return roles.stream().map(role -> {
            Set<Long> assignedResourceIds = resourceIdsByRole.getOrDefault(role.getId(), Set.of());
            List<String> actionCodes = catalogue.stream()
                    .filter(resource -> assignedResourceIds.contains(resource.id())
                            && resource.type() == ResourceType.ACTION)
                    .map(Resource::code)
                    .sorted()
                    .toList();
            return new RoleInfo(role.getId(), role.getName(), actionCodes);
        }).toList();
    }
}
