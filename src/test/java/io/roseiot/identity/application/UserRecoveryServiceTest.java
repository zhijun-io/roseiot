package io.roseiot.identity.application;

import io.roseiot.identity.domain.InvalidRecoveryTokenException;
import io.roseiot.identity.domain.PasswordValidationException;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.infra.recovery.RecoveryNotifier;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRecoveryRequest;
import io.roseiot.identity.persistence.UserRecoveryRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRecoveryServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000006");

    private UserMapper userMapper;
    private UserRecoveryRequestMapper recoveryRequestMapper;
    private RecoveryNotifier recoveryNotifier;
    private UserRefreshSessions refreshSessions;
    private PasswordEncoder passwordEncoder;
    private UserRecoveryService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        recoveryRequestMapper = mock(UserRecoveryRequestMapper.class);
        recoveryNotifier = mock(RecoveryNotifier.class);
        refreshSessions = mock(UserRefreshSessions.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cut = new UserRecoveryService(
                userMapper, recoveryRequestMapper, recoveryNotifier, refreshSessions, passwordEncoder);
    }

    private User existingUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        user.setPasswordHash("old-hash");
        user.setAuthorizationVersion(5);
        return user;
    }

    private UserRecoveryRequest activeRequest() {
        UserRecoveryRequest request = new UserRecoveryRequest();
        request.setId(UUID.randomUUID());
        request.setUserId(USER_ID);
        request.setTokenHash(TokenDigests.sha256Hex("recovery-token"));
        request.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        return request;
    }

    @Test
    void shouldRejectMissingRecoveryTokenBeforeAnyOtherCheck() {
        assertThatThrownBy(() -> cut.confirmRecovery(null, "short1"))
                .isInstanceOf(InvalidRecoveryTokenException.class);
        verifyNoInteractions(recoveryRequestMapper, userMapper, passwordEncoder, refreshSessions);
    }

    @Test
    void shouldRejectBlankRecoveryTokenBeforeAnyOtherCheck() {
        assertThatThrownBy(() -> cut.confirmRecovery("   ", "new passphrase 77"))
                .isInstanceOf(InvalidRecoveryTokenException.class);
        verifyNoInteractions(recoveryRequestMapper, userMapper, passwordEncoder, refreshSessions);
    }

    @Test
    void shouldRejectWeakNewPasswordBeforeLookingUpTheToken() {
        assertThatThrownBy(() -> cut.confirmRecovery("valid-looking-token", "short1"))
                .isInstanceOf(PasswordValidationException.class)
                .hasMessage("Password must be at least 12 characters");
        verifyNoInteractions(recoveryRequestMapper, userMapper, passwordEncoder, refreshSessions);
    }

    @Test
    void shouldReportInvalidTokenRatherThanWeakPasswordWhenBothAreWrong() {
        // The caller must not learn which half of the request was acceptable.
        assertThatThrownBy(() -> cut.confirmRecovery(null, "short1"))
                .isInstanceOf(InvalidRecoveryTokenException.class);
    }

    @Test
    void shouldIssueRecoveryTokenForKnownAccountWithOneHourExpiry() {
        when(userMapper.selectOne(any())).thenReturn(existingUser());
        Instant before = Instant.now();

        String message = cut.requestRecovery("ops@roseiot.io");

        assertThat(message).isEqualTo(UserRecoveryService.ACCEPTED_MESSAGE);
        ArgumentCaptor<UserRecoveryRequest> inserted = ArgumentCaptor.forClass(UserRecoveryRequest.class);
        verify(recoveryRequestMapper).insert(inserted.capture());
        ArgumentCaptor<String> notified = ArgumentCaptor.forClass(String.class);
        verify(recoveryNotifier).sendRecoveryEmail(eq("ops@roseiot.io"), notified.capture());

        // The email must carry the usable secret; the stored row must not.
        UserRecoveryRequest request = inserted.getValue();
        assertThat(request.getTokenHash()).isEqualTo(TokenDigests.sha256Hex(notified.getValue()));
        assertThat(request.getUserId()).isEqualTo(USER_ID);
        assertThat(request.getId()).isNotNull();
        assertThat(request.getUsedAt()).isNull();
        assertThat(request.getExpiresAt()).isBetween(
                before.plus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    void shouldAcceptUnknownRecoveryEmailWithoutIssuingOrNotifying() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThat(cut.requestRecovery("ghost@roseiot.io")).isEqualTo(UserRecoveryService.ACCEPTED_MESSAGE);
        verifyNoInteractions(recoveryRequestMapper, recoveryNotifier, refreshSessions);
    }

    @Test
    void shouldResetPasswordAndConsumeTokenOnValidRecovery() {
        User user = existingUser();
        UserRecoveryRequest active = activeRequest();
        when(recoveryRequestMapper.selectOne(any())).thenReturn(active);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(passwordEncoder.encode("new passphrase 77")).thenReturn("new-hash");

        cut.confirmRecovery("recovery-token", "new passphrase 77");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getAuthorizationVersion()).isEqualTo(6);
        verify(userMapper).updateById(user);
        assertThat(active.getUsedAt()).isNotNull();
        verify(recoveryRequestMapper).updateById(active);
        verify(refreshSessions).revokeAllForUser(eq(USER_ID), any(Instant.class));
    }

    @Test
    void shouldRevokeSessionsWithTheSameInstantThatConsumedTheToken() {
        UserRecoveryRequest active = activeRequest();
        when(recoveryRequestMapper.selectOne(any())).thenReturn(active);
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser());
        when(passwordEncoder.encode(any())).thenReturn("new-hash");

        cut.confirmRecovery("recovery-token", "new passphrase 77");

        ArgumentCaptor<Instant> revokedAt = ArgumentCaptor.forClass(Instant.class);
        verify(refreshSessions).revokeAllForUser(eq(USER_ID), revokedAt.capture());
        assertThat(active.getUsedAt()).isEqualTo(revokedAt.getValue());
    }

    @Test
    void shouldRejectRecoveryWhenTheTokenOwnerIsGone() {
        when(recoveryRequestMapper.selectOne(any())).thenReturn(activeRequest());
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.confirmRecovery("recovery-token", "new passphrase 77"))
                .isInstanceOf(InvalidRecoveryTokenException.class);
        verify(userMapper, never()).updateById(any(User.class));
        verifyNoInteractions(refreshSessions);
    }

    @Test
    void shouldRejectRecoveryWhenNoActiveTokenMatches() {
        when(recoveryRequestMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> cut.confirmRecovery("expired-token", "new passphrase 77"))
                .isInstanceOf(InvalidRecoveryTokenException.class);
        verifyNoInteractions(userMapper, passwordEncoder, refreshSessions);
    }
}
