package io.roseiot.platform.auth.api;

import io.roseiot.platform.auth.PlatformAdminChangePasswordUseCase;
import io.roseiot.platform.auth.PlatformAdminProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/platform")
public class PlatformMeController {

    private final PlatformAdminProfileUseCase platformAdminProfileUseCase;
    private final PlatformAdminChangePasswordUseCase platformAdminChangePasswordUseCase;

    public PlatformMeController(
            PlatformAdminProfileUseCase platformAdminProfileUseCase,
            PlatformAdminChangePasswordUseCase platformAdminChangePasswordUseCase) {
        this.platformAdminProfileUseCase = platformAdminProfileUseCase;
        this.platformAdminChangePasswordUseCase = platformAdminChangePasswordUseCase;
    }

    @GetMapping("/me")
    PlatformMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        PlatformAdminProfileUseCase.PlatformAdminProfile profile = platformAdminProfileUseCase.getProfile(adminId);
        return new PlatformMeResponse(profile.id(), profile.email());
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        platformAdminChangePasswordUseCase.changePassword(
                adminId, request.currentPassword(), request.newPassword());
    }
}
