package io.roseiot.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordResetCredentialService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserMapper users;
    private final PasswordResetTokenMapper resetTokens;
    private final SessionLifecycle sessions;
    private final PasswordEncoder passwordEncoder;
    private final AuthJwtProperties properties;
    private final AuthenticationService authenticationService;

    PasswordResetCredentialService(
            UserMapper users,
            PasswordResetTokenMapper resetTokens,
            SessionLifecycle sessions,
            PasswordEncoder passwordEncoder,
            AuthJwtProperties properties,
            AuthenticationService authenticationService) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.authenticationService = authenticationService;
    }

    @Transactional
    ResetDelivery createResetCredential(String email) {
        AccountCredentials account =
                users.findCredentialsByNormalizedEmail(AuthenticationService.normalizeEmail(email));
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            return null;
        }
        if (users.lockUserForPasswordReset(account.getUserId()) == null) {
            return null;
        }
        account = users.findCredentialsByUserId(account.getUserId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            return null;
        }
        String rawToken = newToken();
        Instant now = Instant.now();
        var row = new PasswordResetTokenRow();
        row.setUserId(account.getUserId());
        row.setTokenDigest(UserSessionService.digest(rawToken));
        row.setCreatedAt(now);
        row.setExpiresAt(now.plus(properties.resetTokenTtl()));
        resetTokens.insert(row);
        return new ResetDelivery(row.getId(), account.getEmail(), rawToken);
    }

    @Transactional
    void completeReset(String rawToken, String newPassword) {
        authenticationService.validateNewPassword(newPassword);
        String tokenDigest = UserSessionService.digest(rawToken);
        Long userId = resetTokens.findUserIdByDigest(tokenDigest);
        if (userId == null || users.lockUserForPasswordReset(userId) == null) {
            throw new InvalidResetTokenException();
        }
        PasswordResetTokenRow token = resetTokens.findByDigestForUpdate(tokenDigest);
        Instant now = Instant.now();
        if (token == null
                || token.getUserId().longValue() != userId.longValue()
                || token.getUsedAt() != null
                || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidResetTokenException();
        }
        AccountCredentials account = users.findCredentialsByUserId(userId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new InvalidResetTokenException();
        }
        if (users.changePassword(userId, passwordEncoder.encode(newPassword)) != 1) {
            throw new InvalidResetTokenException();
        }
        resetTokens.consumeAllOutstandingForUser(userId);
        sessions.revokeAllForUser(userId);
    }

    @Transactional
    void invalidate(long tokenId) {
        resetTokens.invalidate(tokenId);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    record ResetDelivery(long tokenId, String recipient, String rawToken) {}
}
