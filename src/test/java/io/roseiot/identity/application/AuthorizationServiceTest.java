package io.roseiot.identity.application;

import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000002");
    private static final String PERMISSION = "user.profile.read";

    private UserMapper userMapper;
    private JdbcTemplate jdbcTemplate;
    private AuthorizationService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        cut = new AuthorizationService(userMapper, jdbcTemplate);
    }

    private User activeUser(int authorizationVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setAuthorizationVersion(authorizationVersion);
        return user;
    }

    private void stubGrantLookup(int grantedRows) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID), eq(PERMISSION)))
                .thenReturn(grantedRows);
    }

    @Test
    void shouldGrantPermissionWhenActiveTokenMatchesStoredAuthorizationVersion() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(7));
        stubGrantLookup(1);

        assertThat(cut.hasPermission(USER_ID, 7L, PERMISSION)).isTrue();
    }

    @Test
    void shouldDenyWhenUserHasNoRoleCarryingThePermission() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(7));
        stubGrantLookup(0);

        assertThat(cut.hasPermission(USER_ID, 7L, PERMISSION)).isFalse();
    }

    @Test
    void shouldDenyUnknownUserWithoutQueryingGrants() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThat(cut.hasPermission(USER_ID, 7L, PERMISSION)).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldDenyDisabledUserWithoutQueryingGrants() {
        User disabled = activeUser(7);
        disabled.setDisabledAt(Instant.now());
        when(userMapper.selectById(USER_ID)).thenReturn(disabled);

        assertThat(cut.hasPermission(USER_ID, 7L, PERMISSION)).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldDenyStaleTokenAfterAuthorizationVersionChanged() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(8));

        // The token was minted before a role change; its cached version must not grant anything.
        assertThat(cut.hasPermission(USER_ID, 7L, PERMISSION)).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldDenyWhenGrantLookupReportsNoCount() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(7));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID), eq(PERMISSION)))
                .thenReturn(null);

        assertThat(cut.hasPermission(USER_ID, 7L, PERMISSION)).isFalse();
    }
}
