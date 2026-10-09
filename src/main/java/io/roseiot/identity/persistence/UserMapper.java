package io.roseiot.identity.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

public interface UserMapper extends BaseMapper<User> {

    default long countUnscopedUsers() {
        return selectCount(Wrappers.lambdaQuery(User.class).isNull(User::getTenantId));
    }
}
