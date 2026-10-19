package io.roseiot.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserMapper users;
    private final SessionLifecycle sessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyProperties passwordPolicy;
    private final AuthJwtProperties jwtProperties;
    private final AuthAttemptThrottle throttle;

    AuthenticationService(
            AuthenticationManager authenticationManager,
            UserMapper users,
            SessionLifecycle sessions,
            PasswordEncoder passwordEncoder,
            PasswordPolicyProperties passwordPolicy,
            AuthJwtProperties jwtProperties,
            AuthAttemptThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtProperties = jwtProperties;
        this.throttle = throttle;
    }

    @Transactional
    LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (!throttle.allow("login", email, 5, Duration.ofMinutes(15))) {
            throw new RateLimitedException();
        }
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
            var principal = (UserDetailsServiceAdapter.AuthPrincipal) authentication.getPrincipal();
            if (users.lockUserForPasswordReset(principal.userId()) == null) {
                throw new InvalidCredentialsException();
            }
            AccountCredentials current = users.findCredentialsByUserId(principal.userId());
            if (current == null
                    || !"ACTIVE".equals(current.getStatus())
                    || !principal.password().equals(current.getPasswordHash())) {
                throw new InvalidCredentialsException();
            }
            throttle.clear("login", email);
            return LoginResponse.from(sessions.createSession(principal.userId(), current.isPasswordChangeRequired()));
        } catch (AuthenticationException failure) {
            throw new InvalidCredentialsException();
        }
    }

    @Transactional
    PasswordChangeResponse changePassword(long userId, long sessionId, PasswordChangeRequest request) {
        if (users.lockUserForPasswordReset(userId) == null) {
            throw new InvalidCredentialsException();
        }
        AccountCredentials account = users.findCredentialsByUserId(userId);
        if (account == null
                || !"ACTIVE".equals(account.getStatus())
                || !passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        validateNewPassword(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new InvalidPasswordException("The new password must differ from the current password.");
        }
        int changed = users.changePassword(userId, passwordEncoder.encode(request.newPassword()));
        if (changed != 1) {
            throw new IllegalStateException("The account credential could not be updated");
        }
        String accessToken = sessions.issueAccessToken(sessionId, userId, false);
        return new PasswordChangeResponse(
                accessToken, "Bearer", jwtProperties.accessTokenTtl().toSeconds(), false);
    }

    void validateNewPassword(String password) {
        if (password.codePointCount(0, password.length()) < passwordPolicy.minimumLength()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidPasswordException(
                    "The new password must meet the configured minimum length and fit the supported password size.");
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
