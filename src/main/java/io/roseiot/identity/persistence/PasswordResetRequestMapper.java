package io.roseiot.identity.persistence;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.Instant;
import java.util.UUID;

public interface PasswordResetRequestMapper extends BaseMapper<PasswordResetRequest> {

    default int consumeIfActive(UUID id, Instant now) {
        return update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                .set(PasswordResetRequest::getUsedAt, now)
                .eq(PasswordResetRequest::getId, id)
                .isNull(PasswordResetRequest::getUsedAt)
                .gt(PasswordResetRequest::getExpiresAt, now));
    }
}
