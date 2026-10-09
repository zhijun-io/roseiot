package io.roseiot.identity.web;

import io.roseiot.identity.application.AuthorizationService;
import io.roseiot.identity.application.UserProfileService;
import io.roseiot.identity.domain.IdentityJwtClaims;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/users")
public class UserMeController {

    private final UserProfileService accountService;
    private final AuthorizationService authorizationService;

    public UserMeController(UserProfileService accountService, AuthorizationService authorizationService) {
        this.accountService = accountService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/me")
    IdentityMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        requirePermission(jwt, userId, IdentityJwtClaims.PERMISSION_PROFILE_READ);
        UserProfileService.UserProfile profile = accountService.getProfile(userId);
        return new IdentityMeResponse(profile.id(), profile.email());
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        requirePermission(jwt, userId, IdentityJwtClaims.PERMISSION_PROFILE_PASSWORD_WRITE);
        accountService.changePassword(
                userId, request.currentPassword(), request.newPassword());
    }

    private void requirePermission(Jwt jwt, UUID userId, String permissionKey) {
        if (!IdentityJwtClaims.CONTEXT_SYSTEM.equals(jwt.getClaimAsString(IdentityJwtClaims.CONTEXT))) {
            throw new AccessDeniedException("Forbidden");
        }
        long azv = readAuthorizationVersion(jwt);
        if (!authorizationService.hasPermission(userId, azv, permissionKey)) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    private static long readAuthorizationVersion(Jwt jwt) {
        Object claim = jwt.getClaim(IdentityJwtClaims.AUTHORIZATION_VERSION);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        throw new AccessDeniedException("Forbidden");
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record IdentityMeResponse(UUID id, String email) {}
}
