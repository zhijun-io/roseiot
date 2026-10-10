package io.roseiot.iam.persistence;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.Instant;
import java.util.UUID;

public interface UserCredentialMapper extends BaseMapper<UserCredential> {

    default int consumeResetTokenIfActive(UUID userId, String tokenHash, Instant now) {
        return update(null, new LambdaUpdateWrapper<UserCredential>()
                .set(UserCredential::getResetAt, now)
                .set(UserCredential::getUpdatedAt, now)
                .eq(UserCredential::getUserId, userId)
                .eq(UserCredential::getResetToken, tokenHash)
                .isNull(UserCredential::getResetAt)
                .gt(UserCredential::getResetExpiresAt, now));
    }
}
