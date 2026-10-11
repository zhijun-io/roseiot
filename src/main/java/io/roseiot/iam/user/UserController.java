package io.roseiot.iam.user;

import io.roseiot.iam.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
class UserController {

    private final UserService userService;
    private final UserCredentialService credentialService;
    UserController(UserService userService, UserCredentialService credentialService) {
        this.userService = userService;
        this.credentialService = credentialService;
    }

    @GetMapping("/me")
    UserProfile me(@AuthenticationPrincipal SecurityUser user) {
        return userService.getProfile(user.userId());
    }

    @PutMapping("/me")
    UserProfile updateMe(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody UpdateMeRequest request) {
        return userService.updateProfile(user.userId(), request.name());
    }

    @PutMapping("/me/password")
    void changePassword(
            @AuthenticationPrincipal SecurityUser user, @Valid @RequestBody ChangePasswordRequest request) {
        credentialService.changePassword(user.userId(), request.currentPassword(), request.newPassword());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('tenant.user.manage')")
    List<UserInfo> listTenantUsers(@AuthenticationPrincipal SecurityUser user) {
        Long tenantId = user.requireTenantId();
        return userService.listTenantUsers(tenantId);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('tenant.user.manage')")
    void updateTenantUser(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        Long tenantId = user.requireTenantId();
        userService.updateTenantUser(tenantId, userId, request.disabled(), request.roleId());
    }

    @PostMapping("/password-resets")
    void requestPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        credentialService.requestResetByEmail(request.email());
    }

    @GetMapping("/password-resets/{token}")
    Map<String, String> checkResetToken(@PathVariable String token) {
        return Map.of("status", credentialService.checkResetToken(token).name().toLowerCase(Locale.ROOT));
    }

    @PutMapping("/password-resets/{token}")
    void confirmPasswordReset(@PathVariable String token, @Valid @RequestBody PasswordRequest request) {
        credentialService.confirmPasswordReset(token, request.password());
    }

    record UpdateMeRequest(@NotBlank @Size(max = 255) String name) {}
    record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    record ResetPasswordRequest(@NotBlank String email) {}
    record UpdateUserRequest(@NotNull Boolean disabled, @NotNull Long roleId) {}
    record PasswordRequest(@NotBlank String password) {}

}
