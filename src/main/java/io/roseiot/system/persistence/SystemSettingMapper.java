package io.roseiot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

public interface SystemSettingMapper extends BaseMapper<SystemSetting> {
    default SystemSetting selectByType(SystemSettingType type) {
        return selectOne(Wrappers.lambdaQuery(SystemSetting.class).eq(SystemSetting::getType, type));
    }
}
