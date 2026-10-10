package io.roseiot.iam.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.UUID;

public interface RoleMapper extends BaseMapper<Role> {

    default Role selectByCode(UUID tenantId, String code) {
        return selectOne(Wrappers.lambdaQuery(Role.class).eq(Role::getTenantId, tenantId).eq(Role::getCode, code));
    }

    default Role requireByCode(UUID tenantId, String code) {
        Role role = selectByCode(tenantId, code);
        if (role == null) {
            throw new IllegalStateException("Missing seeded role: " + code);
        }
        return role;
    }

    default UUID requireIdByCode(UUID tenantId, String code) {
        return requireByCode(tenantId, code).getId();
    }
}
