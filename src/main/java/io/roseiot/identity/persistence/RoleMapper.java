package io.roseiot.identity.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.UUID;

public interface RoleMapper extends BaseMapper<Role> {

    default Role selectByCode(String code) {
        return selectOne(Wrappers.lambdaQuery(Role.class).eq(Role::getCode, code));
    }

    default Role requireByCode(String code) {
        Role role = selectByCode(code);
        if (role == null) {
            throw new IllegalStateException("Missing seeded role: " + code);
        }
        return role;
    }

    default UUID requireIdByCode(String code) {
        return requireByCode(code).getId();
    }
}
