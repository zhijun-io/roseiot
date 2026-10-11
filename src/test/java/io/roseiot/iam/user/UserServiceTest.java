package io.roseiot.iam.user;






import io.roseiot.iam.role.RoleService;
import io.roseiot.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final Long USER_ID = 1009L;
    private UserMapper userMapper;
    private UserCredentialMapper credentialMapper;
    private UserRoleMapper userRoleMapper;
    private UserService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        credentialMapper = mock(UserCredentialMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        cut = new UserService(userMapper, credentialMapper, userRoleMapper,
                mock(RoleService.class), mock(TenantService.class));
    }

    @Test
    void shouldReturnProfileForExistingUser() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setTenantId(42L);
        user.setEmail("ops@roseiot.io");
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        UserProfile profile = cut.getProfile(USER_ID);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.email()).isEqualTo("ops@roseiot.io");
    }

    @Test
    void shouldHideAccountExistenceFromProfileLookup() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.getProfile(USER_ID)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldLoadAccessTokenDataWithoutReadingCredentials() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setTenantId(42L);
        user.setEmail("ops@roseiot.io");
        user.setStatus(UserStatus.ACTIVE);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userRoleMapper.listPermissionCodesForUser(USER_ID)).thenReturn(List.of("device:read"));

        UserAuthentication authenticationUser = cut.findForAccessToken(USER_ID);

        assertThat(authenticationUser.user().id()).isEqualTo(user.getId());
        assertThat(authenticationUser.user().email()).isEqualTo(user.getEmail());
        assertThat(authenticationUser.password()).isNull();
        assertThat(authenticationUser.permissions()).containsExactly("device:read");
        org.mockito.Mockito.verifyNoInteractions(credentialMapper);
        verify(userRoleMapper).listPermissionCodesForUser(USER_ID);
    }

    @Test
    void shouldNotLoadAuthenticationDataForInactiveUser() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setStatus(UserStatus.DISABLED);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        assertThat(cut.findForAccessToken(USER_ID)).isNull();
    }
}
