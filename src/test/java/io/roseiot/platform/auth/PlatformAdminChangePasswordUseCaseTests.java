package io.roseiot.platform.auth;

import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminChangePasswordUseCaseTests {

    @Mock
    PlatformAdminMapper platformAdminMapper;

    @Mock
    PlatformAdminRefreshSessionRevoker sessionRevoker;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    PlatformAdminChangePasswordUseCase changePasswordUseCase;

    @Test
    void wrongCurrentPasswordRejected() {
        UUID adminId = UUID.randomUUID();
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(adminId);
        admin.setPasswordHash("hash");

        when(platformAdminMapper.selectById(adminId)).thenReturn(admin);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> changePasswordUseCase.changePassword(adminId, "wrong", "newPassword5678"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void successUpdatesHashAndRevokesSessions() {
        UUID adminId = UUID.randomUUID();
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(adminId);
        admin.setPasswordHash("hash");

        when(platformAdminMapper.selectById(adminId)).thenReturn(admin);
        when(passwordEncoder.matches("password1234", "hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword5678")).thenReturn("new-hash");

        changePasswordUseCase.changePassword(adminId, "password1234", "newPassword5678");

        verify(platformAdminMapper).updateById(admin);
        verify(sessionRevoker).revokeAllForAdmin(eq(adminId), any());
    }
}
