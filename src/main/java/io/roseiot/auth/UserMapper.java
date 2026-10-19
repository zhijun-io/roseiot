package io.roseiot.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface UserMapper extends BaseMapper<UserRow> {

    @Select("""
            SELECT user_account.id AS user_id,
                   user_account.email AS email,
                   user_account.status AS status,
                   credential.password_hash AS password_hash,
                   credential.password_change_required AS password_change_required
            FROM users AS user_account
            JOIN user_credentials AS credential ON credential.user_id = user_account.id
            WHERE LOWER(user_account.email) = #{email}
            """)
    @Results(
            id = "accountCredentials",
            value = {
                @Result(property = "userId", column = "user_id"),
                @Result(property = "email", column = "email"),
                @Result(property = "status", column = "status"),
                @Result(property = "passwordHash", column = "password_hash"),
                @Result(property = "passwordChangeRequired", column = "password_change_required")
            })
    AccountCredentials findCredentialsByNormalizedEmail(@Param("email") String normalizedEmail);

    @Select("""
            SELECT user_account.id AS user_id,
                   user_account.email AS email,
                   user_account.status AS status,
                   credential.password_hash AS password_hash,
                   credential.password_change_required AS password_change_required
            FROM users AS user_account
            JOIN user_credentials AS credential ON credential.user_id = user_account.id
            WHERE user_account.id = #{userId}
            """)
    @Results(
            id = "accountCredentialsById",
            value = {
                @Result(property = "userId", column = "user_id"),
                @Result(property = "email", column = "email"),
                @Result(property = "status", column = "status"),
                @Result(property = "passwordHash", column = "password_hash"),
                @Result(property = "passwordChangeRequired", column = "password_change_required")
            })
    AccountCredentials findCredentialsByUserId(@Param("userId") long userId);

    @Select("SELECT id FROM users WHERE id = #{userId} FOR UPDATE")
    Long lockUserForPasswordReset(@Param("userId") long userId);

    @Update("""
            UPDATE user_credentials
            SET password_hash = #{passwordHash},
                password_change_required = FALSE,
                password_changed_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
            """)
    int changePassword(@Param("userId") long userId, @Param("passwordHash") String passwordHash);
}
