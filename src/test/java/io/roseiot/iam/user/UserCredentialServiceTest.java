package io.roseiot.iam.user;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    private static final Long USER_ID = 1008L;
    private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    private UserMapper userMapper;
    private UserCredentialMapper credentialMapper;
    private ApplicationEventPublisher eventPublisher;
    private PasswordEncoder passwordEncoder;
    private UserCredentialService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        credentialMapper = mock(UserCredentialMapper.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cut = new UserCredentialService(userMapper, credentialMapper, eventPublisher, passwordEncoder,
                Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        return user;
    }

    private UserCredentialEntity credential() {
        UserCredentialEntity credential = new UserCredentialEntity();
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
    void shouldRejectPasswordWithoutLettersOrDigitsBeforeLookingUpTheToken() {
        assertThatThrownBy(() -> cut.confirmPasswordReset("valid-token", "123456789012"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password must contain letters and digits");
        verifyNoInteractions(credentialMapper, userMapper, passwordEncoder);
    }

    @Test
    void shouldRejectPasswordsBeyondBcryptUtf8InputLimit() {
        assertThatThrownBy(() -> cut.createInitialPassword(USER_ID, "a".repeat(72) + "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password must not exceed 72 UTF-8 bytes");
        assertThatThrownBy(() -> cut.createInitialPassword(USER_ID, "a".repeat(70) + "é1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password must not exceed 72 UTF-8 bytes");
        verifyNoInteractions(credentialMapper, passwordEncoder);
    }

    @Test
    void shouldAcceptPasswordAtMinimumLength() {
        assertThatThrownBy(() -> cut.confirmPasswordReset("valid-token", "abcdefghij12"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ApiErrorCode.INVALID_RESET_TOKEN.code());
        verify(credentialMapper).selectOne(any());
    }

    @Test
    void shouldIssueOneCurrentResetTokenForKnownAccount() {
        when(userMapper.selectOne(any())).thenReturn(user());
        when(credentialMapper.selectById(USER_ID)).thenReturn(credential());
        Instant before = NOW;

        cut.requestResetByEmail("ops@roseiot.io");

        ArgumentCaptor<UserCredentialEntity> captor = ArgumentCaptor.forClass(UserCredentialEntity.class);
        verify(credentialMapper).updateById(captor.capture());
        ArgumentCaptor<PasswordResetRequestedEvent> event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        UserCredentialEntity stored = captor.getValue();
        assertThat(event.getValue().token()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(stored.getResetToken()).isEqualTo(hash(event.getValue().token()));
        assertThat(event.getValue().email()).isEqualTo("ops@roseiot.io");
        assertThat(stored.getResetAt()).isNull();
        assertThat(stored.getResetExpiresAt()).isBetween(
                before.plus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void shouldNotIssueOrNotifyForUnknownAccount() {
        when(userMapper.selectOne(any())).thenReturn(null);

        cut.requestResetByEmail("ghost@roseiot.io");

        verifyNoInteractions(credentialMapper, eventPublisher);
    }

    @Test
    void shouldMakeOnlyTheNewestRequestedResetTokenCurrent() {
        UserEntity user = user();
        UserCredentialEntity credential = credential();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(credentialMapper.selectById(USER_ID)).thenReturn(credential);

        cut.requestResetByEmail("ops@roseiot.io");
        cut.requestResetByEmail("ops@roseiot.io");

        ArgumentCaptor<PasswordResetRequestedEvent> events = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        assertThat(credential.getResetToken()).isEqualTo(hash(events.getAllValues().get(1).token()));
        assertThat(credential.getResetToken()).isNotEqualTo(hash(events.getAllValues().get(0).token()));
    }

    @Test
    void shouldChangePasswordAfterVerifyingCurrentPassword() {
        UserCredentialEntity credential = credential();
        when(credentialMapper.selectById(USER_ID)).thenReturn(credential);
        when(passwordEncoder.matches("current passphrase 33", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new passphrase 77")).thenReturn("new-hash");

        cut.changePassword(USER_ID, "current passphrase 33", "new passphrase 77");

        assertThat(credential.getPassword()).isEqualTo("new-hash");
        verify(credentialMapper).updateById(credential);
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldCreateInitialPasswordCredential() {
        when(passwordEncoder.encode("new passphrase 77")).thenReturn("new-hash");

        cut.createInitialPassword(USER_ID, "new passphrase 77");

        ArgumentCaptor<UserCredentialEntity> credential = ArgumentCaptor.forClass(UserCredentialEntity.class);
        verify(credentialMapper).insert(credential.capture());
        assertThat(credential.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(credential.getValue().getPassword()).isEqualTo("new-hash");
    }

    @Test
    void shouldReplacePasswordAndConsumeToken() {
        UserCredentialEntity credential = credential();
        credential.setResetToken(hash("reset-token"));
        credential.setResetExpiresAt(NOW.plus(1, ChronoUnit.HOURS));
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
        UserCredentialEntity credential = credential();
        credential.setResetToken(hash("reset-token"));
        credential.setResetExpiresAt(NOW.plus(1, ChronoUnit.HOURS));
        when(credentialMapper.selectOne(any())).thenReturn(credential);
        when(credentialMapper.consumeResetTokenIfActive(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> cut.confirmPasswordReset("reset-token", "new passphrase 77"))
                .isInstanceOf(BusinessException.class);
        verify(credentialMapper, never()).updateById(any(UserCredentialEntity.class));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void shouldClassifyCurrentResetTokenState() {
        UserCredentialEntity credential = credential();
        credential.setResetToken(hash("reset-token"));
        credential.setResetExpiresAt(NOW.plus(1, ChronoUnit.HOURS));
        when(credentialMapper.selectOne(any())).thenReturn(credential);

        assertThat(cut.checkResetToken("reset-token")).isEqualTo(UserCredentialService.ResetTokenState.VALID);
        credential.setResetAt(NOW);
        assertThat(cut.checkResetToken("reset-token")).isEqualTo(UserCredentialService.ResetTokenState.INVALID);
        credential.setResetAt(null);
        credential.setResetExpiresAt(NOW.minusSeconds(1));
        assertThat(cut.checkResetToken("reset-token")).isEqualTo(UserCredentialService.ResetTokenState.EXPIRED);
        assertThat(cut.checkResetToken(" ")).isEqualTo(UserCredentialService.ResetTokenState.INVALID);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
