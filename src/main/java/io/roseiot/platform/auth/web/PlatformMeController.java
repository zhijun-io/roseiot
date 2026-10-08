package io.roseiot.platform.auth.web;

import io.roseiot.platform.auth.application.PlatformAdminAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    private final PlatformAdminAccountService accountService;

    public PlatformMeController(PlatformAdminAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    PlatformMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        PlatformAdminAccountService.PlatformAdminProfile profile = accountService.getProfile(adminId);
        return new PlatformMeResponse(profile.id(), profile.email());
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        accountService.changePassword(
                adminId, request.currentPassword(), request.newPassword());
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record PlatformMeResponse(UUID id, String email) {}
}
