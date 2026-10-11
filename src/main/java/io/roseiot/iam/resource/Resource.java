package io.roseiot.iam.resource;


/** Public resource model; ResourceEntity and its mapper are internal to this feature. */
public record Resource(
        Long id,
        Long tenantId,
        String code,
        String name,
        ResourceType type,
        Long parentId,
        ResourceStatus status,
        String route,
        String icon,
        int sortOrder) {

    static Resource from(ResourceEntity entity) {
        return new Resource(entity.getId(), entity.getTenantId(), entity.getCode(), entity.getName(),
                entity.getType(), entity.getParentId(), entity.getStatus(), entity.getRoute(),
                entity.getIcon(), entity.getSortOrder());
    }
}
