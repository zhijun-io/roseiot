package io.roseiot.iam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserCredentialService {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final UserCredentialMapper credentialMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserCredentialService(
            UserMapper userMapper,
            UserCredentialMapper credentialMapper,
            ApplicationEventPublisher eventPublisher,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void createInitialPassword(Long userId, String password) {
        ensureValidPassword(password);
        UserCredential credential =
                new UserCredential(userId, passwordEncoder.encode(password), null, null, null, null, null);
        credentialMapper.insert(UserCredentialEntity.from(credential));
    }

    @Transactional
    @PreAuthorize("isAuthenticated() and authentication.principal instanceof T(io.roseiot.shared.security.SecurityUser)"
            + " and #p0 == authentication.principal.userId()")
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserCredentialEntity credential = credentialMapper.selectById(userId);
        if (credential == null
                || currentPassword == null
                || !passwordEncoder.matches(currentPassword, credential.getPassword())) {
            throw new BadCredentialsException(ApiErrorCode.INVALID_CREDENTIALS.code());
        }
        ensureValidPassword(newPassword);

        credential.setPassword(passwordEncoder.encode(newPassword));
        credentialMapper.updateById(credential);
    }

    @Transactional
    public void requestResetByEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (user == null) return;
        UserCredentialEntity credential = credentialMapper.selectById(user.getId());
        if (credential == null) return;

        String plainToken = generateResetToken();
        Instant now = Instant.now(clock);
        credential.setResetToken(hashResetToken(plainToken));
        credential.setResetExpiresAt(now.plus(1, ChronoUnit.HOURS));
        credential.setResetAt(null);
        credentialMapper.updateById(credential);
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), plainToken));
    }

    public ResetTokenState checkResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return ResetTokenState.INVALID;
        String tokenHash = hashResetToken(rawToken);
        UserCredentialEntity credential = credentialMapper.selectOne(
                new LambdaQueryWrapper<UserCredentialEntity>().eq(UserCredentialEntity::getResetToken, tokenHash));
        if (credential == null || credential.getResetAt() != null) return ResetTokenState.INVALID;
        if (credential.getResetExpiresAt() == null
                || !credential.getResetExpiresAt().isAfter(Instant.now(clock))) {
            return ResetTokenState.EXPIRED;
        }
        return ResetTokenState.VALID;
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
        }
        ensureValidPassword(newPassword);

        String tokenHash = hashResetToken(rawToken);
        Instant now = Instant.now(clock);
        UserCredentialEntity credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredentialEntity>()
                .eq(UserCredentialEntity::getResetToken, tokenHash)
                .isNull(UserCredentialEntity::getResetAt)
                .gt(UserCredentialEntity::getResetExpiresAt, now));
        if (credential == null
                || credentialMapper.consumeResetTokenIfActive(credential.getUserId(), tokenHash, now) != 1) {
            throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
        }

        credential.setResetAt(now);
        credential.setPassword(passwordEncoder.encode(newPassword));
        credentialMapper.updateById(credential);
    }

    public enum ResetTokenState {
        VALID,
        EXPIRED,
        INVALID
    }

    private static String generateResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashResetToken(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static void ensureValidPassword(String password) {
        if (password == null || password.length() < 12) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Password must be at least 12 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Password must not exceed 72 UTF-8 bytes");
        }
        if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Password must contain letters and digits");
        }
    }
}
