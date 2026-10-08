package io.roseiot.platform.auth.api;

import io.roseiot.platform.auth.PlatformAdminLogoutUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/auth")
public class AuthSessionController {

    private final PlatformAdminLogoutUseCase platformAdminLogoutUseCase;

    public AuthSessionController(PlatformAdminLogoutUseCase platformAdminLogoutUseCase) {
        this.platformAdminLogoutUseCase = platformAdminLogoutUseCase;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal Jwt jwt) {
        UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        platformAdminLogoutUseCase.logoutCurrentSession(sessionId);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        platformAdminLogoutUseCase.logoutAllSessions(adminId);
    }
}
