package io.roseiot.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

interface TenantMapper extends BaseMapper<TenantEntity> {

    default TenantEntity selectByIdForUpdate(Long tenantId) {
        return selectOne(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getId, tenantId)
                .last("FOR UPDATE"));
    }
}
