package io.roseiot.identity.application;

import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import org.springframework.security.authentication.BadCredentialsException;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");

    private UserMapper userMapper;
    private UserRefreshSessions refreshSessions;
    private PasswordEncoder passwordEncoder;
    private UserProfileService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        refreshSessions = mock(UserRefreshSessions.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cut = new UserProfileService(userMapper, refreshSessions, passwordEncoder);
    }

    private User storedUser(int authorizationVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        user.setPasswordHash("stored-hash");
        user.setAuthorizationVersion(authorizationVersion);
        return user;
    }

    @Test
    void shouldReturnProfileForExistingUser() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(1));

        UserProfileService.UserProfile profile = cut.getProfile(USER_ID);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.email()).isEqualTo("ops@roseiot.io");
    }

    @Test
    void shouldHideAccountExistenceFromProfileLookup() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.getProfile(USER_ID)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldReplaceHashAndBumpAuthorizationVersionOnPasswordChange() {
        User user = storedUser(3);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(passwordEncoder.matches("correct horse 42", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("new passphrase 77")).thenReturn("new-hash");
        Instant before = Instant.now();

        cut.changePassword(USER_ID, "correct horse 42", "new passphrase 77");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getAuthorizationVersion()).isEqualTo(4);
        verify(userMapper).updateById(user);
        ArgumentCaptor<Instant> revokedAt = ArgumentCaptor.forClass(Instant.class);
        verify(refreshSessions).revokeAllForUser(any(UUID.class), revokedAt.capture());
        assertThat(revokedAt.getValue()).isAfterOrEqualTo(before);
    }

    @Test
    void shouldRejectWrongCurrentPasswordWithoutTouchingStorage() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(3));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> cut.changePassword(USER_ID, "wrong password 1", "new passphrase 77"))
                .isInstanceOf(BadCredentialsException.class);
        verify(userMapper, never()).updateById(any(User.class));
        verifyNoInteractions(refreshSessions);
    }

    @Test
    void shouldRejectMissingCurrentPasswordWithoutConsultingTheEncoder() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(3));

        assertThatThrownBy(() -> cut.changePassword(USER_ID, null, "new passphrase 77"))
                .isInstanceOf(BadCredentialsException.class);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void shouldRejectUnknownUserBeforeComparingPasswords() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.changePassword(USER_ID, "correct horse 42", "new passphrase 77"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectWeakNewPasswordKeepingTheOldHashAndSessions() {
        User user = storedUser(3);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(passwordEncoder.matches("correct horse 42", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> cut.changePassword(USER_ID, "correct horse 42", "short1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password must be at least 12 characters");
        assertThat(user.getPasswordHash()).isEqualTo("stored-hash");
        assertThat(user.getAuthorizationVersion()).isEqualTo(3);
        verify(userMapper, never()).updateById(any(User.class));
        verifyNoInteractions(refreshSessions);
    }
}
