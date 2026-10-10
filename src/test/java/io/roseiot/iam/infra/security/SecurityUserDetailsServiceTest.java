package io.roseiot.iam.infra.security;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.domain.UserStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserCredential;
import io.roseiot.iam.persistence.UserCredentialMapper;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.iam.persistence.UserRoleMapper;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
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
    private static final UUID TENANT_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000f1");

    private UserMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private UserCredentialMapper credentialMapper;
    private TenantMapper tenantMapper;
    private SecurityUserDetailsService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        credentialMapper = mock(UserCredentialMapper.class);
        tenantMapper = mock(TenantMapper.class);
        cut = new SecurityUserDetailsService(userMapper, userRoleMapper, credentialMapper, tenantMapper);
    }

    @Test
    void shouldLoadActiveTenantUserWithActionAuthoritiesOnly() {
        User user = activeUser();
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(activeTenant());
        when(credentialMapper.selectById(USER_ID)).thenReturn(activeCredential());
        when(userRoleMapper.listPermissionCodesForUser(USER_ID))
                .thenReturn(List.of(ActionCode.TENANT_USER_MANAGE, ActionCode.DEVICE_READ));

        SecurityUser details = (SecurityUser) cut.loadUserByUsername("Ops@roseiot.io");

        assertThat(details.userId()).isEqualTo(USER_ID);
        assertThat(details.tenantId()).isEqualTo(TENANT_ID);
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly(ActionCode.TENANT_USER_MANAGE, ActionCode.DEVICE_READ);
    }

    @Test
    void shouldLoadUserByIdThroughRefreshPath() {
        User user = activeUser();
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(activeTenant());
        when(credentialMapper.selectById(USER_ID)).thenReturn(activeCredential());
        when(userRoleMapper.listPermissionCodesForUser(USER_ID)).thenReturn(List.of());

        SecurityUser details = cut.loadByUserId(USER_ID);

        assertThat(details.userId()).isEqualTo(USER_ID);
        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    void shouldTreatDisabledUserAsNotFound() {
        User disabled = activeUser();
        disabled.setStatus(UserStatus.DISABLED);
        when(userMapper.selectById(USER_ID)).thenReturn(disabled);

        assertThatThrownBy(() -> cut.loadByUserId(USER_ID)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldTreatUserOfMissingTenantAsNotFound() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser());
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.loadByUserId(USER_ID)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldTreatUserOfDisabledTenantAsNotFound() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser());
        Tenant disabledTenant = activeTenant();
        disabledTenant.setDisabledAt(Instant.now());
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(disabledTenant);

        assertThatThrownBy(() -> cut.loadByUserId(USER_ID)).isInstanceOf(UsernameNotFoundException.class);
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setEmail("ops@roseiot.io");
        user.setAuthorizationVersion(1);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserCredential activeCredential() {
        UserCredential credential = new UserCredential();
        credential.setUserId(USER_ID);
        credential.setPasswordHash("hash");
                return credential;
    }

    private Tenant activeTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setSlug("default");
        return tenant;
    }
}
