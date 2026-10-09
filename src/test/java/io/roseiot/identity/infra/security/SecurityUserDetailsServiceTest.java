package io.roseiot.identity.infra.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUserDetailsServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000003");

    private UserMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private SecurityUserDetailsService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        cut = new SecurityUserDetailsService(userMapper, userRoleMapper);
    }

    @Test
    void shouldLoadActiveUserWithPermissionAuthorities() {
        User user = activeUser();
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(userRoleMapper.listPermissionCodesForUser(USER_ID)).thenReturn(List.of("user.profile.read"));
        when(userRoleMapper.listRoleCodesForUser(USER_ID)).thenReturn(List.of("SystemAdmin"));

        SecurityUser details = (SecurityUser) cut.loadUserByUsername("Ops@roseiot.io");

        assertThat(details.userId()).isEqualTo(USER_ID);
        assertThat(details.isSystemUser()).isTrue();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("user.profile.read", "ROLE_SystemAdmin");
    }

    @Test
    void shouldTreatUserWithTenantIdAsTenantUser() {
        User user = activeUser();
        user.setTenantId(UUID.randomUUID());
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        SecurityUser details = cut.loadByUserId(USER_ID);

        assertThat(details.isTenantUser()).isTrue();
        assertThat(details.isSystemUser()).isFalse();
    }

    @Test
    void shouldTreatDisabledUserAsNotFound() {
        User disabled = activeUser();
        disabled.setDisabledAt(Instant.now());
        when(userMapper.selectById(USER_ID)).thenReturn(disabled);

        assertThatThrownBy(() -> cut.loadByUserId(USER_ID)).isInstanceOf(UsernameNotFoundException.class);
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        user.setPasswordHash("hash");
        user.setAuthorizationVersion(1);
        return user;
    }
}
