package io.roseiot.iam.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.iam.domain.LoginEmails;
import io.roseiot.iam.domain.PasswordPolicy;
import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.iam.infra.notifier.PasswordResetNotifier;
import io.roseiot.iam.infra.security.AuthenticationExceptionMapping;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserCredential;
import io.roseiot.iam.persistence.UserCredentialMapper;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class UserCredentialService {

    private final UserMapper userMapper;
    private final UserCredentialMapper credentialMapper;
    private final PasswordResetNotifier passwordResetNotifier;
    private final PasswordEncoder passwordEncoder;

    public UserCredentialService(
            UserMapper userMapper,
            UserCredentialMapper credentialMapper,
            PasswordResetNotifier passwordResetNotifier,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.passwordResetNotifier = passwordResetNotifier;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        UserCredential credential = credentialMapper.selectById(userId);
        if (user == null || credential == null || currentPassword == null
                || !passwordEncoder.matches(currentPassword, credential.getPassword())) {
            throw AuthenticationExceptionMapping.invalidCredentials();
        }
        PasswordPolicy.ensureValid(newPassword, ApiErrorCode.VALIDATION_ERROR);

        credential.setPassword(passwordEncoder.encode(newPassword));
        credential.setUpdatedAt(Instant.now());
        credentialMapper.updateById(credential);
    }

    @Transactional
    public void requestResetByEmail(String rawEmail) {
        String email = LoginEmails.normalize(rawEmail);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) return;
        UserCredential credential = credentialMapper.selectById(user.getId());
        if (credential == null) return;

        String plainToken = TokenDigests.generateUrlSafe256Bit();
        Instant now = Instant.now();
        credential.setResetToken(TokenDigests.sha256Hex(plainToken));
        credential.setResetExpiresAt(now.plus(1, ChronoUnit.HOURS));
        credential.setResetAt(null);
        credential.setUpdatedAt(now);
        credentialMapper.updateById(credential);
        passwordResetNotifier.sendPasswordResetEmail(user.getEmail(), plainToken);
    }

    public ResetTokenState checkResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return ResetTokenState.INVALID;
        String tokenHash = TokenDigests.sha256Hex(rawToken);
        UserCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getResetToken, tokenHash));
        if (credential == null || credential.getResetAt() != null) return ResetTokenState.INVALID;
        if (credential.getResetExpiresAt() == null || !credential.getResetExpiresAt().isAfter(Instant.now())) {
            return ResetTokenState.EXPIRED;
        }
        return ResetTokenState.VALID;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void confirmPasswordReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
        }
        PasswordPolicy.ensureValid(newPassword, ApiErrorCode.VALIDATION_ERROR);

        String tokenHash = TokenDigests.sha256Hex(rawToken);
        Instant now = Instant.now();
        UserCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getResetToken, tokenHash)
                .isNull(UserCredential::getResetAt)
                .gt(UserCredential::getResetExpiresAt, now));
        if (credential == null || credentialMapper.consumeResetTokenIfActive(credential.getUserId(), tokenHash, now) != 1) {
            throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
        }

        credential.setResetAt(now);
        credential.setPassword(passwordEncoder.encode(newPassword));
        credential.setUpdatedAt(now);
        credentialMapper.updateById(credential);
        User user = userMapper.selectById(credential.getUserId());
        if (user == null) throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
    }

    public enum ResetTokenState { VALID, EXPIRED, INVALID }
}
