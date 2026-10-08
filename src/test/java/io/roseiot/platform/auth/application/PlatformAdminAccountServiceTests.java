package io.roseiot.platform.auth.application;

import io.roseiot.platform.auth.domain.InvalidCredentialsException;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminAccountServiceTests {

    @Mock
    PlatformAdminMapper platformAdminMapper;

    @Mock
    PlatformAdminRefreshSessions refreshSessions;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    PlatformAdminAccountService accountService;

    @Test
    void getProfile() {
        UUID id = UUID.randomUUID();
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(id);
        admin.setEmail("admin@example.com");
        when(platformAdminMapper.selectById(id)).thenReturn(admin);

        var profile = accountService.getProfile(id);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.email()).isEqualTo("admin@example.com");
    }

    @Test
    void getProfileRejectsMissingAdmin() {
        UUID id = UUID.randomUUID();
        when(platformAdminMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> accountService.getProfile(id)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongCurrentPasswordRejected() {
        UUID adminId = UUID.randomUUID();
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(adminId);
        admin.setPasswordHash("hash");

        when(platformAdminMapper.selectById(adminId)).thenReturn(admin);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(adminId, "wrong", "newPassword5678"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void changePasswordUpdatesHashAndRevokesSessions() {
        UUID adminId = UUID.randomUUID();
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(adminId);
        admin.setPasswordHash("hash");

        when(platformAdminMapper.selectById(adminId)).thenReturn(admin);
        when(passwordEncoder.matches("password1234", "hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword5678")).thenReturn("new-hash");

        accountService.changePassword(adminId, "password1234", "newPassword5678");

        verify(platformAdminMapper).updateById(admin);
        verify(refreshSessions).revokeAllForAdmin(eq(adminId), any());
    }
}
