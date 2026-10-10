package io.roseiot.iam.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.Instant;
import java.util.UUID;

public interface UserSessionMapper extends BaseMapper<UserSession> {

    default UserSession selectActiveById(UUID id, Instant now) {
        return selectOne(new LambdaQueryWrapper<UserSession>()
                .select(UserSession::getId, UserSession::getUserId)
                .eq(UserSession::getId, id)
                .isNull(UserSession::getRevokedAt)
                .gt(UserSession::getExpiresAt, now));
    }

    default int revokeForRotation(UUID id, Instant now) {
        return update(null, new LambdaUpdateWrapper<UserSession>()
                .set(UserSession::getRevokedAt, now)
                .eq(UserSession::getId, id)
                .isNull(UserSession::getRevokedAt)
                .gt(UserSession::getExpiresAt, now));
    }
}
