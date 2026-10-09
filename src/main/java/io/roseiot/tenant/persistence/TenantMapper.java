package io.roseiot.tenant.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;

import java.util.UUID;

public interface TenantMapper extends BaseMapper<Tenant> {

    default void requireActive(UUID tenantId) {
        Tenant tenant = selectById(tenantId);
        if (tenant == null || tenant.getDisabledAt() != null) {
            throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        }
    }
}
