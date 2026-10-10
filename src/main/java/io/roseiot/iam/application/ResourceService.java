package io.roseiot.iam.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.domain.ResourceScope;
import io.roseiot.iam.domain.ResourceType;
import io.roseiot.iam.persistence.Resource;
import io.roseiot.iam.persistence.ResourceMapper;
import io.roseiot.iam.persistence.UserRoleMapper;
import io.roseiot.tenant.persistence.TenantMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ResourceService {

    private final ResourceMapper resources;
    private final UserRoleMapper assignments;
    private final TenantMapper tenants;

    public ResourceService(ResourceMapper resources, UserRoleMapper assignments, TenantMapper tenants) {
        this.resources = resources;
        this.assignments = assignments;
        this.tenants = tenants;
    }

    public List<Resource> catalog() {
        return resources.selectList(Wrappers.lambdaQuery(Resource.class)
                .eq(Resource::getTenantId, tenants.requireDefaultTenantId())
                .eq(Resource::getActive, true)
                .orderByAsc(Resource::getSortOrder, Resource::getCode));
    }

    public List<Resource> findActiveActions(Set<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return resources.selectList(Wrappers.lambdaQuery(Resource.class)
                .eq(Resource::getType, ResourceType.ACTION)
                .eq(Resource::getActive, true)
                .in(Resource::getCode, codes));
    }

    public List<Resource> assignableActions(UUID tenantId) {
        tenants.requireCustomerTenant(tenantId);
        return catalog().stream()
                .filter(r -> r.getType() == ResourceType.ACTION)
                .filter(r -> r.getScope() == ResourceScope.TENANT || r.getScope() == ResourceScope.BOTH)
                .toList();
    }

    public List<Node> assignableTree(UUID tenantId) {
        Set<String> codes = new HashSet<>();
        assignableActions(tenantId).forEach(r -> codes.add(r.getCode()));
        return tree(codes);
    }

    public List<Node> userTree(UUID userId) {
        return tree(new HashSet<>(assignments.listPermissionCodesForUser(userId)));
    }

    private List<Node> tree(Set<String> codes) {
        var all = catalog();
        Map<UUID, Resource> byId = new HashMap<>();
        all.forEach(r -> byId.put(r.getId(), r));
        Set<UUID> included = new HashSet<>();
        for (Resource action : all) {
            if (action.getType() != ResourceType.ACTION || !codes.contains(action.getCode())) continue;
            Resource current = action;
            Set<UUID> path = new HashSet<>();
            while (current != null && path.add(current.getId())) {
                included.add(current.getId());
                current = byId.get(current.getParentId());
            }
        }
        return children(null, all, included);
    }

    private List<Node> children(UUID parentId, List<Resource> all, Set<UUID> included) {
        return all.stream()
                .filter(r -> included.contains(r.getId()) && Objects.equals(parentId, r.getParentId()))
                .map(r -> new Node(r.getId(), r.getCode(), r.getName(), r.getType(), r.getRoute(), r.getIcon(),
                        r.getSortOrder(), children(r.getId(), all, included)))
                .toList();
    }

    public record Node(UUID id, String code, String name, ResourceType type, String route, String icon,
                       int sortOrder, List<Node> children) {}
}
