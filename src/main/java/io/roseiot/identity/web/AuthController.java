package io.roseiot.identity.web;

import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.infra.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api")
public class AuthController {

    private final UserSessionService sessionService;

    public AuthController(UserSessionService sessionService) {
        this.sessionService = sessionService;
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
    void logout(@AuthenticationPrincipal SecurityUser user) {
        sessionService.logoutCurrentSession(user.requireSessionId());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal SecurityUser user) {
        sessionService.logoutAllSessions(user.userId());
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record TokenRefreshRequest(@NotBlank String refreshToken) {}

    public record LoginResponse(String accessToken, String refreshToken) {

        public static LoginResponse from(UserSessionService.SessionTokens tokens) {
            return new LoginResponse(tokens.accessToken(), tokens.refreshToken());
        }
    }
}
