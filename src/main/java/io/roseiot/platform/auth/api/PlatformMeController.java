package io.roseiot.platform.auth.api;

import io.roseiot.platform.auth.PlatformAdminProfileUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/platform")
public class PlatformMeController {

    private final PlatformAdminProfileUseCase platformAdminProfileUseCase;

    public PlatformMeController(PlatformAdminProfileUseCase platformAdminProfileUseCase) {
        this.platformAdminProfileUseCase = platformAdminProfileUseCase;
    }

    @GetMapping("/me")
    PlatformMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        PlatformAdminProfileUseCase.PlatformAdminProfile profile = platformAdminProfileUseCase.getProfile(adminId);
        return new PlatformMeResponse(profile.id(), profile.email());
    }
}
