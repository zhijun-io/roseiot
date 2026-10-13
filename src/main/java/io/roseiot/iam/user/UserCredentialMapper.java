package io.roseiot.iam.user;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;

interface UserCredentialMapper extends BaseMapper<UserCredentialEntity> {

    default int consumeResetTokenIfActive(Long userId, String tokenHash, Instant now) {
        return update(
                null,
                new LambdaUpdateWrapper<UserCredentialEntity>()
                        .set(UserCredentialEntity::getResetAt, now)
                        .set(UserCredentialEntity::getUpdatedAt, now)
                        .eq(UserCredentialEntity::getUserId, userId)
                        .eq(UserCredentialEntity::getResetToken, tokenHash)
                        .isNull(UserCredentialEntity::getResetAt)
                        .gt(UserCredentialEntity::getResetExpiresAt, now));
    }
}
