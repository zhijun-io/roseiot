package io.roseiot.identity.web;

import io.roseiot.identity.application.UserRecoveryService;
import io.roseiot.identity.application.UserSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/identity")
public class AuthController {

    private final UserSessionService sessionService;
    private final UserRecoveryService recoveryService;

    public AuthController(UserSessionService sessionService, UserRecoveryService recoveryService) {
        this.sessionService = sessionService;
        this.recoveryService = recoveryService;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(sessionService.login(request.email(), request.password()));
    }

    @PostMapping("/token")
    LoginResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return LoginResponse.from(sessionService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal Jwt jwt) {
        UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        sessionService.logoutCurrentSession(sessionId);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        sessionService.logoutAllSessions(userId);
    }

    @PostMapping("/recovery/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RecoveryRequestResponse recoveryRequest(@Valid @RequestBody RecoveryRequest request) {
        String message = recoveryService.requestRecovery(request.email());
        return new RecoveryRequestResponse(message);
    }

    @PostMapping("/recovery/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recoveryConfirm(@Valid @RequestBody RecoveryConfirmRequest request) {
        recoveryService.confirmRecovery(request.token(), request.newPassword());
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record TokenRefreshRequest(@NotBlank String refreshToken) {}

    public record RecoveryRequest(@NotBlank String email) {}

    public record RecoveryConfirmRequest(@NotBlank String token, @NotBlank String newPassword) {}

    public record LoginResponse(String accessToken, String refreshToken) {

        public static LoginResponse from(UserSessionService.SessionTokens tokens) {
            return new LoginResponse(tokens.accessToken(), tokens.refreshToken());
        }
    }

    public record RecoveryRequestResponse(String message) {}
}
