package io.roseiot.identity.application;

import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthorizationService {

    private static final String COUNT_USER_PERMISSION = """
            SELECT COUNT(*)
            FROM user_role ur
            JOIN role_permission rp ON rp.role_id = ur.role_id
            JOIN permission p ON p.id = rp.permission_id
            WHERE ur.user_id = ? AND p.code = ?
            """;

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    public AuthorizationService(UserMapper userMapper, JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasPermission(UUID userId, long authorizationVersion, String permissionKey) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDisabledAt() != null) {
            return false;
        }
        if (user.getAuthorizationVersion() != authorizationVersion) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(COUNT_USER_PERMISSION, Integer.class, userId, permissionKey);
        return count != null && count > 0;
    }
}
