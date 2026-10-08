package io.roseiot.identity.application;

import io.roseiot.identity.domain.InvalidCredentialsException;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
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
class UserAccountServiceTests {

    @Mock
    UserMapper userMapper;

    @Mock
    UserRefreshSessions refreshSessions;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserAccountService accountService;

    @Test
    void getProfile() {
        UUID id = UUID.randomUUID();
        User admin = new User();
        admin.setId(id);
        admin.setEmail("admin@example.com");
        when(userMapper.selectById(id)).thenReturn(admin);

        var profile = accountService.getProfile(id);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.email()).isEqualTo("admin@example.com");
    }

    @Test
    void getProfileRejectsMissingUser() {
        UUID id = UUID.randomUUID();
        when(userMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> accountService.getProfile(id)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongCurrentPasswordRejected() {
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        admin.setPasswordHash("hash");

        when(userMapper.selectById(adminId)).thenReturn(admin);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(adminId, "wrong", "newPassword5678"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void changePasswordUpdatesHashAndRevokesSessions() {
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        admin.setPasswordHash("hash");
        admin.setAuthorizationVersion(1);

        when(userMapper.selectById(adminId)).thenReturn(admin);
        when(passwordEncoder.matches("password1234", "hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword5678")).thenReturn("new-hash");

        accountService.changePassword(adminId, "password1234", "newPassword5678");

        assertThat(admin.getAuthorizationVersion()).isEqualTo(2);
        verify(userMapper).updateById(admin);
        verify(refreshSessions).revokeAllForUser(eq(adminId), any());
    }
}
