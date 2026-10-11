package io.roseiot.iam.resource;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.tenant.Tenant;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ResourceService {

    private final ResourceMapper resourceMapper;

    public ResourceService(ResourceMapper resourceMapper) {
        this.resourceMapper = resourceMapper;
    }

    public List<Resource> catalog() {
        return resourceMapper.selectList(Wrappers.lambdaQuery(ResourceEntity.class)
                        .eq(ResourceEntity::getTenantId, Tenant.DEFAULT_TENANT_ID)
                        .eq(ResourceEntity::getStatus, ResourceStatus.ACTIVE)
                        .orderByAsc(ResourceEntity::getSortOrder, ResourceEntity::getCode))
                .stream().map(Resource::from).toList();
    }

    @PreAuthorize("isAuthenticated()")
    public org.springframework.data.domain.Page<ResourceInfo> list(Pageable pageable) {
        Pageable checkedPageable = checkedPageable(pageable);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ResourceEntity> result = resourceMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        checkedPageable.getPageNumber() + 1L, checkedPageable.getPageSize()),
                Wrappers.lambdaQuery(ResourceEntity.class)
                        .eq(ResourceEntity::getTenantId, Tenant.DEFAULT_TENANT_ID)
                        .eq(ResourceEntity::getStatus, ResourceStatus.ACTIVE)
                        .orderByAsc(ResourceEntity::getId));
        List<ResourceInfo> items = result.getRecords().stream()
                .map(Resource::from).map(ResourceInfo::from).toList();
        return new PageImpl<>(items, checkedPageable, result.getTotal());
    }

    private static Pageable checkedPageable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100
                || !Sort.by(Sort.Order.asc("id")).equals(pageable.getSort())) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Use a page size from 1 to 100 and sort by id ascending");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
    }

    public List<Resource> findAssignableActions(Set<String> actionCodes) {
        if (actionCodes == null) {
            throw new IllegalArgumentException("actionCodes is required");
        }
        if (actionCodes.isEmpty()) {
            return List.of();
        }
        var query = Wrappers.lambdaQuery(ResourceEntity.class)
                .eq(ResourceEntity::getTenantId, Tenant.DEFAULT_TENANT_ID)
                .eq(ResourceEntity::getType, ResourceType.ACTION)
                .eq(ResourceEntity::getStatus, ResourceStatus.ACTIVE);
        query.in(ResourceEntity::getCode, actionCodes);
        return resourceMapper.selectList(query)
                .stream().map(Resource::from)
                .filter(resource -> !Set.of(
                                "tenant.create", "tenant.disable", "tenant.enable", "tenant.read",
                                "system.setting.manage")
                        .contains(resource.code()))
                .toList();
    }

}
