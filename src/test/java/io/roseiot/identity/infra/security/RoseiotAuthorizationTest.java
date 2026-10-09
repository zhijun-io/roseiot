package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoseiotAuthorizationTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000002");
    private static final String PERMISSION = IdentityJwtClaims.PERMISSION_PROFILE_READ;

    private UserMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private RoseiotAuthorization cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        cut = new RoseiotAuthorization(userMapper, userRoleMapper);
    }

    private UsernamePasswordAuthenticationToken authentication(long authorizationVersion) {
        SecurityUser user = new SecurityUser(
                USER_ID, authorizationVersion, IdentityJwtClaims.CONTEXT_SYSTEM, null, null);
        return UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of());
    }

    private User activeUser(int authorizationVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setAuthorizationVersion(authorizationVersion);
        return user;
    }

    @Test
    void shouldGrantPermissionWhenActiveTokenMatchesStoredAuthorizationVersion() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(7));
        when(userRoleMapper.userHasPermission(USER_ID, PERMISSION)).thenReturn(true);

        assertThat(cut.hasPermission(authentication(7L), PERMISSION)).isTrue();
    }

    @Test
    void shouldDenyWhenUserHasNoRoleCarryingThePermission() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(7));
        when(userRoleMapper.userHasPermission(USER_ID, PERMISSION)).thenReturn(false);

        assertThat(cut.hasPermission(authentication(7L), PERMISSION)).isFalse();
    }

    @Test
    void shouldDenyUnknownUserWithoutQueryingGrants() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThat(cut.hasPermission(authentication(7L), PERMISSION)).isFalse();
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    void shouldDenyDisabledUserWithoutQueryingGrants() {
        User disabled = activeUser(7);
        disabled.setDisabledAt(Instant.now());
        when(userMapper.selectById(USER_ID)).thenReturn(disabled);

        assertThat(cut.hasPermission(authentication(7L), PERMISSION)).isFalse();
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    void shouldDenyStaleTokenAfterAuthorizationVersionChanged() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(8));

        assertThat(cut.hasPermission(authentication(7L), PERMISSION)).isFalse();
        verifyNoInteractions(userRoleMapper);
    }
}
