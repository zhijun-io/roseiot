package io.roseiot.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
interface SessionRefreshTokenMapper extends BaseMapper<SessionRefreshTokenRow> {
    @Update("""
            UPDATE user_session_refresh_tokens
            SET revoked_at = CURRENT_TIMESTAMP
            WHERE session_id IN (SELECT id FROM user_sessions WHERE user_id = #{userId})
              AND revoked_at IS NULL
            """)
    int revokeRefreshTokensForUser(@Param("userId") long userId);
}
