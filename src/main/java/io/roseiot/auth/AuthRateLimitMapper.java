package io.roseiot.auth;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AuthRateLimitMapper {
    @Select(value = """
            INSERT INTO auth_rate_limits (request_key, window_started_at, attempt_count)
            VALUES (#{requestKey}, CURRENT_TIMESTAMP, 1)
            ON CONFLICT (request_key) DO UPDATE SET
                window_started_at = CASE
                    WHEN auth_rate_limits.window_started_at <= CURRENT_TIMESTAMP - (#{windowSeconds} * INTERVAL '1 second')
                    THEN CURRENT_TIMESTAMP ELSE auth_rate_limits.window_started_at END,
                attempt_count = CASE
                    WHEN auth_rate_limits.window_started_at <= CURRENT_TIMESTAMP - (#{windowSeconds} * INTERVAL '1 second')
                    THEN 1 ELSE auth_rate_limits.attempt_count + 1 END,
                updated_at = CURRENT_TIMESTAMP
            RETURNING attempt_count
            """, affectData = true)
    int recordAttempt(@Param("requestKey") String requestKey, @Param("windowSeconds") long windowSeconds);

    @Delete("DELETE FROM auth_rate_limits WHERE request_key = #{requestKey}")
    int clear(@Param("requestKey") String requestKey);

    @Delete("DELETE FROM auth_rate_limits WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL '2 days'")
    int deleteExpired();
}
