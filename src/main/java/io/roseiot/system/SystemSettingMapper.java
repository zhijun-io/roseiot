package io.roseiot.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

interface SystemSettingMapper extends BaseMapper<SystemSettingEntity> {
    default SystemSettingEntity selectByType(SystemSettingType type) {
        return selectOne(Wrappers.lambdaQuery(SystemSettingEntity.class)
                .eq(SystemSettingEntity::getType, type));
    }
}
