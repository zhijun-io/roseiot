package io.roseiot.iam.application;

import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.iam.infra.notifier.PasswordResetNotifier;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserCredential;
import io.roseiot.iam.persistence.UserCredentialMapper;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
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

class UserCredentialServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000006");
    private UserMapper userMapper;
    private UserCredentialMapper credentialMapper;
    private PasswordResetNotifier notifier;
    private PasswordEncoder passwordEncoder;
    private UserCredentialService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        credentialMapper = mock(UserCredentialMapper.class);
        notifier = mock(PasswordResetNotifier.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cut = new UserCredentialService(userMapper, credentialMapper, notifier, passwordEncoder);
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        return user;
    }

    private UserCredential credential() {
        UserCredential credential = new UserCredential();
        credential.setUserId(USER_ID);
        credential.setPassword("old-hash");
        return credential;
    }

    @Test
    void shouldRejectMissingResetTokenBeforeAnyOtherCheck() {
        assertThatThrownBy(() -> cut.confirmPasswordReset(null, "short1"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(credentialMapper, userMapper, passwordEncoder);
    }

    @Test
    void shouldRejectWeakNewPasswordBeforeLookingUpTheToken() {
        assertThatThrownBy(() -> cut.confirmPasswordReset("valid-token", "short1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password must be at least 12 characters");
        verifyNoInteractions(credentialMapper, userMapper, passwordEncoder);
    }

    @Test
    void shouldIssueOneCurrentResetTokenForKnownAccount() {
        when(userMapper.selectOne(any())).thenReturn(user());
        when(credentialMapper.selectById(USER_ID)).thenReturn(credential());
        Instant before = Instant.now();

        cut.requestResetByEmail("ops@roseiot.io");

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(credentialMapper).updateById(captor.capture());
        ArgumentCaptor<String> notified = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendPasswordResetEmail(eq("ops@roseiot.io"), notified.capture());
        UserCredential stored = captor.getValue();
        assertThat(stored.getResetToken()).isEqualTo(TokenDigests.sha256Hex(notified.getValue()));
        assertThat(stored.getResetAt()).isNull();
        assertThat(stored.getUpdatedAt()).isBetween(before, Instant.now());
        assertThat(stored.getResetExpiresAt()).isBetween(
                before.plus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    void shouldNotIssueOrNotifyForUnknownAccount() {
        when(userMapper.selectOne(any())).thenReturn(null);

        cut.requestResetByEmail("ghost@roseiot.io");

        verifyNoInteractions(credentialMapper, notifier);
    }

    @Test
    void shouldMakeOnlyTheNewestRequestedResetTokenCurrent() {
        User user = user();
        UserCredential credential = credential();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(credentialMapper.selectById(USER_ID)).thenReturn(credential);

        cut.requestResetByEmail("ops@roseiot.io");
        cut.requestResetByEmail("ops@roseiot.io");

        ArgumentCaptor<String> notified = ArgumentCaptor.forClass(String.class);
        verify(notifier, org.mockito.Mockito.times(2))
                .sendPasswordResetEmail(eq("ops@roseiot.io"), notified.capture());
        assertThat(credential.getResetToken()).isEqualTo(TokenDigests.sha256Hex(notified.getAllValues().get(1)));
        assertThat(credential.getResetToken()).isNotEqualTo(TokenDigests.sha256Hex(notified.getAllValues().get(0)));
    }

    @Test
    void shouldChangePasswordAfterVerifyingCurrentPassword() {
        User user = user();
        UserCredential credential = credential();
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(credentialMapper.selectById(USER_ID)).thenReturn(credential);
        when(passwordEncoder.matches("current passphrase 33", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new passphrase 77")).thenReturn("new-hash");

        cut.changePassword(USER_ID, "current passphrase 33", "new passphrase 77");

        assertThat(credential.getPassword()).isEqualTo("new-hash");
        verify(credentialMapper).updateById(credential);
    }

    @Test
    void shouldReplacePasswordAndConsumeToken() {
        UserCredential credential = credential();
        credential.setResetToken(TokenDigests.sha256Hex("reset-token"));
        credential.setResetExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(credentialMapper.selectOne(any())).thenReturn(credential);
        when(credentialMapper.consumeResetTokenIfActive(eq(USER_ID), eq(credential.getResetToken()), any()))
                .thenReturn(1);
        when(passwordEncoder.encode("new passphrase 77")).thenReturn("new-hash");

        cut.confirmPasswordReset("reset-token", "new passphrase 77");

        assertThat(credential.getPassword()).isEqualTo("new-hash");
        assertThat(credential.getResetAt()).isNotNull();
        verify(credentialMapper).updateById(credential);
    }

    @Test
    void shouldRejectResetWhenAnotherRequestReplacedTheToken() {
        when(credentialMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> cut.confirmPasswordReset("old-token", "new passphrase 77"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ApiErrorCode.INVALID_RESET_TOKEN.code());
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void shouldRejectConcurrentResetConsumerWithoutChangingPassword() {
        UserCredential credential = credential();
        credential.setResetToken(TokenDigests.sha256Hex("reset-token"));
        credential.setResetExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(credentialMapper.selectOne(any())).thenReturn(credential);
        when(credentialMapper.consumeResetTokenIfActive(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> cut.confirmPasswordReset("reset-token", "new passphrase 77"))
                .isInstanceOf(BusinessException.class);
        verify(credentialMapper, never()).updateById(any(UserCredential.class));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void shouldClassifyCurrentResetTokenState() {
        UserCredential credential = credential();
        credential.setResetToken(TokenDigests.sha256Hex("reset-token"));
        credential.setResetExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(credentialMapper.selectOne(any())).thenReturn(credential);

        assertThat(cut.checkResetToken("reset-token")).isEqualTo(UserCredentialService.ResetTokenState.VALID);
        credential.setResetAt(Instant.now());
        assertThat(cut.checkResetToken("reset-token")).isEqualTo(UserCredentialService.ResetTokenState.INVALID);
        credential.setResetAt(null);
        credential.setResetExpiresAt(Instant.now().minusSeconds(1));
        assertThat(cut.checkResetToken("reset-token")).isEqualTo(UserCredentialService.ResetTokenState.EXPIRED);
        assertThat(cut.checkResetToken(" ")).isEqualTo(UserCredentialService.ResetTokenState.INVALID);
    }
}
