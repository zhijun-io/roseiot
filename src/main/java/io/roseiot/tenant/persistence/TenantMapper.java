package io.roseiot.tenant.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.security.access.AccessDeniedException;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;

import java.util.UUID;

public interface TenantMapper extends BaseMapper<Tenant> {

    @Select("SELECT id FROM tenant WHERE slug = 'default'")
    UUID findDefaultTenantId();

    default UUID requireDefaultTenantId() {
        UUID id = findDefaultTenantId();
        if (id == null) throw new IllegalStateException("Missing reserved default tenant");
        return id;
    }

    default void requireCustomerTenant(UUID tenantId) {
        Tenant tenant = selectById(tenantId);
        if (tenant != null && "default".equals(tenant.getSlug())) {
            throw new AccessDeniedException("Reserved tenant");
        }
    }

    default Tenant selectByIdForUpdate(UUID tenantId) {
        return selectOne(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getId, tenantId)
                .last("FOR UPDATE"));
    }

    default void requireActive(UUID tenantId) {
        ensureActive(selectById(tenantId));
    }

    default void requireActiveForUpdate(UUID tenantId) {
        ensureActive(selectByIdForUpdate(tenantId));
    }

    private static void ensureActive(Tenant tenant) {
        if (tenant == null || tenant.getDisabledAt() != null) {
            throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        }
    }
}
