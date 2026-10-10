package io.roseiot.iam.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.domain.ResourceStatus;
import io.roseiot.iam.domain.ResourceType;
import io.roseiot.iam.persistence.Resource;
import io.roseiot.iam.persistence.ResourceMapper;
import io.roseiot.tenant.persistence.TenantMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ResourceService {

    private final ResourceMapper resources;
    private final TenantMapper tenants;

    public ResourceService(ResourceMapper resources, TenantMapper tenants) {
        this.resources = resources;
        this.tenants = tenants;
    }

    public List<Resource> catalog() {
        return resources.selectList(Wrappers.lambdaQuery(Resource.class)
                .eq(Resource::getTenantId, tenants.requireDefaultTenantId())
                .eq(Resource::getStatus, ResourceStatus.ACTIVE)
                .orderByAsc(Resource::getSortOrder, Resource::getCode));
    }

    public List<Resource> findActiveActions(Set<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return resources.selectList(Wrappers.lambdaQuery(Resource.class)
                .eq(Resource::getType, ResourceType.ACTION)
                .eq(Resource::getStatus, ResourceStatus.ACTIVE)
                .in(Resource::getCode, codes));
    }

    public List<Resource> assignableActions(UUID tenantId) {
        tenants.requireCustomerTenant(tenantId);
        return catalog().stream()
                .filter(r -> r.getType() == ResourceType.ACTION)
                .filter(r -> !ActionCode.PLATFORM_ONLY.contains(r.getCode()))
                .toList();
    }
}
