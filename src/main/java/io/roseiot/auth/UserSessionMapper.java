package io.roseiot.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface UserSessionMapper extends BaseMapper<UserSessionRow> {

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM user_sessions AS session
                JOIN users AS user_account ON user_account.id = session.user_id
                WHERE session.id = #{sessionId}
                  AND session.user_id = #{userId}
                  AND session.revoked_at IS NULL
                  AND session.expires_at > #{now}
                  AND user_account.status = 'ACTIVE'
            )
            """)
    boolean isActive(@Param("sessionId") long sessionId, @Param("userId") long userId, @Param("now") Instant now);

    @Update("""
            UPDATE user_sessions
            SET revoked_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND revoked_at IS NULL
            """)
    int revokeAllForUser(@Param("userId") long userId);
}
