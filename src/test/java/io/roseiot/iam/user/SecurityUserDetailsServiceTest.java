package io.roseiot.iam.user;

import io.roseiot.shared.security.SecurityUser;
import io.roseiot.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUserDetailsServiceTest {

    private static final Long USER_ID = 1002L;
    private static final Long TENANT_ID = 42L;

    private UserService userService;
    private TenantService tenantService;
    private SecurityUserDetailsService cut;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        tenantService = mock(TenantService.class);
        cut = new SecurityUserDetailsService(userService, tenantService);
    }

    @Test
    void shouldLoadActiveTenantUserWithActionAuthoritiesOnly() {
        when(userService.findForAuthentication("ops@roseiot.io")).thenReturn(Optional.of(authenticationUser()));
        when(tenantService.isActive(TENANT_ID)).thenReturn(true);

        SecurityUser details = (SecurityUser) cut.loadUserByUsername("Ops@roseiot.io");

        assertThat(details.userId()).isEqualTo(USER_ID);
        assertThat(details.tenantId()).isEqualTo(TENANT_ID);
        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactly("tenant.user.manage", "device.read");
    }

    @Test
    void shouldLoadUserById() {
        when(userService.findForAccessToken(USER_ID)).thenReturn(Optional.of(authenticationUser()));
        when(tenantService.isActive(TENANT_ID)).thenReturn(true);

        SecurityUser details = cut.loadByUserId(USER_ID);

        assertThat(details.userId()).isEqualTo(USER_ID);
        assertThat(details.getAuthorities()).hasSize(2);
    }

    @Test
    void shouldTreatMissingUserAsNotFound() {
        when(userService.findForAccessToken(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cut.loadByUserId(USER_ID)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldTreatUserOfMissingTenantAsNotFound() {
        when(userService.findForAccessToken(USER_ID)).thenReturn(Optional.of(authenticationUser()));
        when(tenantService.isActive(TENANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> cut.loadByUserId(USER_ID)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldTreatUserOfDisabledTenantAsNotFound() {
        when(userService.findForAccessToken(USER_ID)).thenReturn(Optional.of(authenticationUser()));
        when(tenantService.isActive(TENANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> cut.loadByUserId(USER_ID)).isInstanceOf(UsernameNotFoundException.class);
    }

    private UserAuthentication authenticationUser() {
        User user = new User(USER_ID, TENANT_ID, "ops@roseiot.io", null, UserStatus.ACTIVE);
        return new UserAuthentication(
                user, "hash", List.of("tenant.user.manage", "device.read"));
    }
}
