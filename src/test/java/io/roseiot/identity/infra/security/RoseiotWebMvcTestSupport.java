package io.roseiot.identity.infra.security;

import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public final class RoseiotWebMvcTestSupport {

    private RoseiotWebMvcTestSupport() {}

    public static User activeUser(UUID userId, long authorizationVersion) {
        User user = new User();
        user.setId(userId);
        user.setAuthorizationVersion(Math.toIntExact(authorizationVersion));
        return user;
    }

    public static void grantEveryPermission(
            UserMapper userMapper, UserRoleMapper userRoleMapper, UUID userId, long authorizationVersion) {
        User user = activeUser(userId, authorizationVersion);
        given(userMapper.selectById(userId)).willReturn(user);
        given(userMapper.selectById(any(UUID.class))).willReturn(user);
        given(userRoleMapper.userHasPermission(any(), anyString())).willReturn(true);
    }
}
