package io.roseiot.identity.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.Instant;
import java.util.UUID;

public interface UserRefreshSessionMapper extends BaseMapper<UserRefreshSession> {

    default UserRefreshSession selectActiveById(UUID id, Instant now) {
        return selectOne(new LambdaQueryWrapper<UserRefreshSession>()
                .select(UserRefreshSession::getId, UserRefreshSession::getUserId)
                .eq(UserRefreshSession::getId, id)
                .isNull(UserRefreshSession::getRevokedAt)
                .gt(UserRefreshSession::getExpiresAt, now));
    }

    default int revokeForRotation(UUID id, Instant now) {
        return update(null, new LambdaUpdateWrapper<UserRefreshSession>()
                .set(UserRefreshSession::getRevokedAt, now)
                .eq(UserRefreshSession::getId, id)
                .isNull(UserRefreshSession::getRevokedAt)
                .gt(UserRefreshSession::getExpiresAt, now));
    }
}
