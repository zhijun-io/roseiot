package io.roseiot.identity.application;

import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.infra.passwordreset.PasswordResetNotifier;
import io.roseiot.identity.persistence.PasswordResetRequest;
import io.roseiot.identity.persistence.PasswordResetRequestMapper;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000006");

    private UserMapper userMapper;
    private PasswordResetRequestMapper passwordResetRequestMapper;
    private PasswordResetNotifier passwordResetNotifier;
    private UserRefreshSessions refreshSessions;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordResetRequestMapper = mock(PasswordResetRequestMapper.class);
        passwordResetNotifier = mock(PasswordResetNotifier.class);
        refreshSessions = mock(UserRefreshSessions.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cut = new PasswordResetService(
                userMapper, passwordResetRequestMapper, passwordResetNotifier, refreshSessions, passwordEncoder);
    }

    private User existingUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        user.setPasswordHash("old-hash");
        user.setAuthorizationVersion(5);
        return user;
    }

    private PasswordResetRequest activeRequest() {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setId(UUID.randomUUID());
        request.setUserId(USER_ID);
        request.setTokenHash(TokenDigests.sha256Hex("reset-token"));
        request.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        return request;
    }

    @Test
    void shouldRejectMissingResetTokenBeforeAnyOtherCheck() {
        assertThatThrownBy(() -> cut.confirmPasswordReset(null, "short1"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(passwordResetRequestMapper, userMapper, passwordEncoder, refreshSessions);
    }

    @Test
    void shouldRejectBlankResetTokenBeforeAnyOtherCheck() {
        assertThatThrownBy(() -> cut.confirmPasswordReset("   ", "new passphrase 77"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(passwordResetRequestMapper, userMapper, passwordEncoder, refreshSessions);
    }

    @Test
    void shouldRejectWeakNewPasswordBeforeLookingUpTheToken() {
        assertThatThrownBy(() -> cut.confirmPasswordReset("valid-looking-token", "short1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password must be at least 12 characters");
        verifyNoInteractions(passwordResetRequestMapper, userMapper, passwordEncoder, refreshSessions);
    }

    @Test
    void shouldReportInvalidTokenRatherThanWeakPasswordWhenBothAreWrong() {
        assertThatThrownBy(() -> cut.confirmPasswordReset(null, "short1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldIssueResetTokenForKnownAccountWithOneHourExpiry() {
        when(userMapper.selectOne(any())).thenReturn(existingUser());
        Instant before = Instant.now();

        cut.requestResetByEmail("ops@roseiot.io");

        ArgumentCaptor<PasswordResetRequest> inserted = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(passwordResetRequestMapper).insert(inserted.capture());
        ArgumentCaptor<String> notified = ArgumentCaptor.forClass(String.class);
        verify(passwordResetNotifier).sendPasswordResetEmail(eq("ops@roseiot.io"), notified.capture());

        PasswordResetRequest request = inserted.getValue();
        assertThat(request.getTokenHash()).isEqualTo(TokenDigests.sha256Hex(notified.getValue()));
        assertThat(request.getUserId()).isEqualTo(USER_ID);
        assertThat(request.getId()).isNotNull();
        assertThat(request.getUsedAt()).isNull();
        assertThat(request.getExpiresAt()).isBetween(
                before.plus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    void shouldAcceptUnknownEmailWithoutIssuingOrNotifying() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatCode(() -> cut.requestResetByEmail("ghost@roseiot.io")).doesNotThrowAnyException();
        verifyNoInteractions(passwordResetRequestMapper, passwordResetNotifier, refreshSessions);
    }

    @Test
    void shouldResetPasswordAndConsumeTokenOnValidReset() {
        User user = existingUser();
        PasswordResetRequest active = activeRequest();
        when(passwordResetRequestMapper.selectOne(any())).thenReturn(active);
        when(passwordResetRequestMapper.consumeIfActive(eq(active.getId()), any(Instant.class))).thenReturn(1);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(passwordEncoder.encode("new passphrase 77")).thenReturn("new-hash");

        cut.confirmPasswordReset("reset-token", "new passphrase 77");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getAuthorizationVersion()).isEqualTo(6);
        verify(userMapper).updateById(user);
        verify(passwordResetRequestMapper).consumeIfActive(eq(active.getId()), any(Instant.class));
        verify(refreshSessions).revokeAllForUser(eq(USER_ID), any(Instant.class));
    }

    @Test
    void shouldRevokeSessionsWithTheSameInstantThatConsumedTheToken() {
        PasswordResetRequest active = activeRequest();
        when(passwordResetRequestMapper.selectOne(any())).thenReturn(active);
        when(passwordResetRequestMapper.consumeIfActive(eq(active.getId()), any(Instant.class))).thenReturn(1);
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser());
        when(passwordEncoder.encode(any())).thenReturn("new-hash");

        cut.confirmPasswordReset("reset-token", "new passphrase 77");

        ArgumentCaptor<Instant> revokedAt = ArgumentCaptor.forClass(Instant.class);
        verify(refreshSessions).revokeAllForUser(eq(USER_ID), revokedAt.capture());
        verify(passwordResetRequestMapper).consumeIfActive(eq(active.getId()), eq(revokedAt.getValue()));
    }

    @Test
    void shouldRejectResetWhenTheTokenOwnerIsGone() {
        PasswordResetRequest active = activeRequest();
        when(passwordResetRequestMapper.selectOne(any())).thenReturn(active);
        when(passwordResetRequestMapper.consumeIfActive(eq(active.getId()), any(Instant.class))).thenReturn(1);
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.confirmPasswordReset("reset-token", "new passphrase 77"))
                .isInstanceOf(BusinessException.class);
        verify(userMapper, never()).updateById(any(User.class));
        verifyNoInteractions(refreshSessions);
    }

    @Test
    void shouldClassifyResetTokenStateForCheckResetToken() {
        PasswordResetRequest active = activeRequest();
        when(passwordResetRequestMapper.selectOne(any())).thenReturn(active);

        assertThat(cut.checkResetToken("reset-token")).isEqualTo(PasswordResetService.ResetTokenState.VALID);

        active.setUsedAt(Instant.now());
        assertThat(cut.checkResetToken("reset-token")).isEqualTo(PasswordResetService.ResetTokenState.INVALID);

        active.setUsedAt(null);
        active.setExpiresAt(Instant.now().minusSeconds(1));
        assertThat(cut.checkResetToken("reset-token")).isEqualTo(PasswordResetService.ResetTokenState.EXPIRED);

        assertThat(cut.checkResetToken("  ")).isEqualTo(PasswordResetService.ResetTokenState.INVALID);
    }

    @Test
    void shouldRejectResetWhenNoActiveTokenMatches() {
        when(passwordResetRequestMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> cut.confirmPasswordReset("expired-token", "new passphrase 77"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(userMapper, passwordEncoder, refreshSessions);
    }

    @Test
    void shouldNotChangePasswordWhenAnotherRequestConsumedTheTokenFirst() {
        PasswordResetRequest active = activeRequest();
        when(passwordResetRequestMapper.selectOne(any())).thenReturn(active);
        when(passwordResetRequestMapper.consumeIfActive(eq(active.getId()), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> cut.confirmPasswordReset("reset-token", "new passphrase 77"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ApiErrorCode.INVALID_RESET_TOKEN.code());
        verifyNoInteractions(userMapper, passwordEncoder, refreshSessions);
    }
}
