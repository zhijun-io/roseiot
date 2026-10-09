package io.roseiot.identity.web;

import io.roseiot.identity.application.UserProfileService;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.infra.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService accountService;

    public UserProfileController(UserProfileService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    @PreAuthorize("@authz.hasPermission(authentication, '" + IdentityJwtClaims.PERMISSION_PROFILE_READ + "')")
    IdentityMeResponse me(@AuthenticationPrincipal SecurityUser user) {
        return IdentityMeResponse.from(accountService.getProfile(user.userId()));
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authz.hasPermission(authentication, '" + IdentityJwtClaims.PERMISSION_PROFILE_PASSWORD_WRITE + "')")
    void changePassword(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(user.userId(), request.currentPassword(), request.newPassword());
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record IdentityMeResponse(UUID id, String email) {

        static IdentityMeResponse from(UserProfileService.UserProfile profile) {
            return new IdentityMeResponse(profile.id(), profile.email());
        }
    }
}
