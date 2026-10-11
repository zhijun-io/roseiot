package io.roseiot.iam.resource;

/** Public API view of an active resource; excludes persistence and tenant-internal fields. */
record ResourceInfo(
        Long id,
        String code,
        String name,
        ResourceType type,
        Long parentId,
        String route,
        String icon,
        int sortOrder) {

    static ResourceInfo from(Resource resource) {
        return new ResourceInfo(resource.id(), resource.code(), resource.name(), resource.type(),
                resource.parentId(), resource.route(), resource.icon(), resource.sortOrder());
    }
}
