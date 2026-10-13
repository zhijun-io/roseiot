package io.roseiot.iam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.iam.role.RoleService;
import io.roseiot.shared.security.SecurityUser;
import io.roseiot.tenant.TenantService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class UserServiceTest {

    private static final Long USER_ID = 1009L;
    private UserMapper userMapper;
    private UserCredentialMapper credentialMapper;
    private UserRoleMapper userRoleMapper;
    private RoleService roleService;
    private TenantService tenantService;
    private UserCredentialService credentialService;
    private UserService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        credentialMapper = mock(UserCredentialMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        roleService = mock(RoleService.class);
        tenantService = mock(TenantService.class);
        credentialService = mock(UserCredentialService.class);
        cut = new UserService(
                userMapper,
                credentialMapper,
                userRoleMapper,
                roleService,
                tenantService,
                credentialService,
                Clock.systemUTC());
    }

    @Test
    void shouldReturnProfileForExistingUser() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setTenantId(42L);
        user.setEmail("ops@roseiot.io");
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        User profile = cut.getProfile(USER_ID);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.email()).isEqualTo("ops@roseiot.io");
    }

    @Test
    void shouldHideAccountExistenceFromProfileLookup() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.getProfile(USER_ID)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldLoadUserByNormalizedEmailWithCurrentAuthorities() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setTenantId(42L);
        user.setEmail("ops@roseiot.io");
        user.setStatus(UserStatus.ACTIVE);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any(LambdaQueryWrapper.class)))
                .thenReturn(user);
        when(tenantService.isActive(42L)).thenReturn(true);
        UserCredentialEntity credential = new UserCredentialEntity();
        credential.setPassword("encoded-password");
        when(credentialMapper.selectById(USER_ID)).thenReturn(credential);
        when(userRoleMapper.listPermissionCodesForUser(USER_ID)).thenReturn(List.of("device:read"));

        SecurityUser principal = (SecurityUser) cut.loadUserByUsername(" OPS@ROSEIOT.IO ");

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.getUsername()).isEqualTo(user.getEmail());
        assertThat(principal.getPassword()).isEqualTo("encoded-password");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("device:read");
        verify(userRoleMapper).listPermissionCodesForUser(USER_ID);
    }

    @Test
    void shouldLoadJwtPrincipalByStableUserIdWithoutLoadingPasswordHash() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setTenantId(42L);
        user.setEmail("ops@roseiot.io");
        user.setStatus(UserStatus.ACTIVE);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(tenantService.isActive(42L)).thenReturn(true);
        when(userRoleMapper.listPermissionCodesForUser(USER_ID)).thenReturn(List.of("device:read"));

        SecurityUser principal = cut.loadByUserId(USER_ID);

        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.getUsername()).isEqualTo("ops@roseiot.io");
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("device:read");
        org.mockito.Mockito.verifyNoInteractions(credentialMapper);
    }

    @Test
    void shouldRejectInactiveUser() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setStatus(UserStatus.DISABLED);
        user.setEmail("ops@roseiot.io");
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any(LambdaQueryWrapper.class)))
                .thenReturn(user);

        assertThatThrownBy(() -> cut.loadUserByUsername("ops@roseiot.io"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldRejectUserOfDisabledTenant() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setTenantId(42L);
        user.setEmail("ops@roseiot.io");
        user.setStatus(UserStatus.ACTIVE);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any(LambdaQueryWrapper.class)))
                .thenReturn(user);
        when(tenantService.isActive(42L)).thenReturn(false);

        assertThatThrownBy(() -> cut.loadUserByUsername("ops@roseiot.io"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldCreateUserAndInitialCredentialInCurrentTenant() {
        when(userMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(userMapper.insert(org.mockito.ArgumentMatchers.any(UserEntity.class)))
                .thenAnswer(invocation -> {
                    UserEntity user = invocation.getArgument(0);
                    user.setId(USER_ID);
                    return 1;
                });

        UserResponse created = cut.create(42L, " NEW@EXAMPLE.TEST ", " New User ", "passphrase1234", 7L);

        assertThat(created).isEqualTo(new UserResponse(USER_ID, "new@example.test", "New User", 7L, false));
        verify(tenantService).requireCustomerTenant(42L);
        verify(tenantService).requireActiveForUpdate(42L);
        verify(roleService).requireAssignable(42L, 7L);
        verify(credentialService).createInitialPassword(USER_ID, "passphrase1234");
        verify(userRoleMapper).insertAssignment(USER_ID, 7L, 42L);
        verify(roleService).ensureAdministrator(42L);
    }
}
