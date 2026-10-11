package io.roseiot.iam.resource;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.tenant.Tenant;
import io.roseiot.shared.webmvc.CursorPage;
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

    public CursorPage<Resource> list(String cursor, int limit) {
        long afterId = CursorPage.afterId(cursor);
        int pageSize = CursorPage.checkedLimit(limit);
        List<Resource> rows = resourceMapper.selectList(Wrappers.lambdaQuery(ResourceEntity.class)
                        .eq(ResourceEntity::getTenantId, Tenant.DEFAULT_TENANT_ID)
                        .eq(ResourceEntity::getStatus, ResourceStatus.ACTIVE)
                        .gt(ResourceEntity::getId, afterId)
                        .orderByAsc(ResourceEntity::getId)
                        .last("LIMIT " + (pageSize + 1)))
                .stream().map(Resource::from).toList();
        return CursorPage.fromRows(rows, pageSize, Resource::id);
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
