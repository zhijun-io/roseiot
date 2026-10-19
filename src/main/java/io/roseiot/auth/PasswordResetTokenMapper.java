package io.roseiot.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenRow> {
    @Select("""
            SELECT id, user_id, token_digest, created_at, expires_at, used_at
            FROM password_reset_tokens
            WHERE token_digest = #{digest}
            FOR UPDATE
            """)
    @Results(
            id = "passwordResetToken",
            value = {
                @Result(property = "id", column = "id"),
                @Result(property = "userId", column = "user_id"),
                @Result(property = "tokenDigest", column = "token_digest"),
                @Result(property = "createdAt", column = "created_at"),
                @Result(property = "expiresAt", column = "expires_at"),
                @Result(property = "usedAt", column = "used_at")
            })
    PasswordResetTokenRow findByDigestForUpdate(@Param("digest") String digest);

    @Update("UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP "
            + "WHERE user_id = #{userId} AND used_at IS NULL")
    int invalidateUnusedForUser(@Param("userId") long userId);

    @Update("UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP WHERE id = #{id} AND used_at IS NULL")
    int invalidate(@Param("id") long id);

    @Update("UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP WHERE id = #{id} AND used_at IS NULL")
    int markUsed(@Param("id") long id);
}
