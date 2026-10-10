package io.roseiot.iam.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.roseiot.iam.domain.ResourceScope;
import io.roseiot.iam.domain.ResourceType;

import java.util.UUID;

@TableName("resource")
public class Resource {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID tenantId;
    private String code;
    private String name;
    private ResourceType type;
    private UUID parentId;
    private ResourceScope scope;
    private boolean active;
    private String route;
    private String icon;
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ResourceType getType() { return type; }
    public void setType(ResourceType type) { this.type = type; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public ResourceScope getScope() { return scope; }
    public void setScope(ResourceScope scope) { this.scope = scope; }
    public boolean getActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
